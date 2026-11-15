# IoT 设备接入与智能诊断平台

一个能跑真数据的物联网后端：设备通过 **MQTT** 上报遥测，平台做**时序入库、规则告警、工单流转**，并在告警产生时调用**大模型做根因诊断**。没有 API Key 也能完整跑通——诊断会自动降级到本地规则引擎。

> 做这个项目的出发点：把"设备 → 网关 → 云端 → 应用"这条链路真正跑一遍，而不是停在 PPT 上。
> 所以选了内嵌 MQTT Broker，`clone` 下来不需要装 Docker、不需要装 EMQX，`mvn spring-boot:run` 就能起。

---

## 一、它是怎么跑起来的

```
┌─────────────┐   MQTT/TCP :1883    ┌──────────────────────────────────────────┐
│  设备/模拟器  │ ──────────────────▶ │  Moquette Broker（内嵌在同一 JVM）        │
│  deviceKey   │  iot/{pk}/{dk}/     │    ├─ DeviceAuthenticator  一机一密校验    │
│  + secret    │      telemetry      │    └─ TelemetryInterceptor 抓 PUBLISH     │
└─────────────┘                     └───────────────────┬──────────────────────┘
                                                        │ 线程池异步
                                                        ▼
                                            ┌───────────────────────┐
                                            │ TelemetryIngestService │
                                            │  解析 → 入库 → 更新影子 │
                                            └───────────┬───────────┘
                                                        │
                          ┌─────────────────────────────┼──────────────────────────┐
                          ▼                             ▼                          ▼
                 ┌─────────────────┐         ┌──────────────────┐       ┌──────────────────┐
                 │ telemetry 时序表 │         │ 设备影子（Caffeine）│       │   AlarmEngine     │
                 │ (device,metric,ts)│        │  最新状态快照      │       │ 连续 N 点防抖判定  │
                 └─────────────────┘         └──────────────────┘       └────────┬─────────┘
                                                                                 │ 命中
                                                                                 ▼
                                                                      ┌────────────────────┐
                                                                      │ alarm 工单（状态机） │
                                                                      │ TRIGGERED→ACKED→CLOSED│
                                                                      └─────────┬──────────┘
                                                                                │ 事件（AFTER_COMMIT + 异步）
                                                                                ▼
                                                                      ┌────────────────────┐
                                                                      │ AiDiagnosisService │
                                                                      │ DashScope / Mock   │
                                                                      │ 超时 12s 自动降级   │
                                                                      └────────────────────┘
```

HTTP 侧是 Spring Boot + Spring Security 6 + JWT，接口文档走 Knife4j，看板是 `static/index.html` 一个单页（原生 JS + ECharts，无构建步骤）。

---

## 二、快速开始

### 前置

| 需要 | 版本 | 说明 |
|---|---|---|
| JDK | 21 | 用了 record、switch 模式匹配、虚拟线程 |
| Maven | 3.9+ | |
| MySQL | 8.0+ | 只需建一个空库，表由应用启动时自动建 |

```sql
CREATE DATABASE iot_platform DEFAULT CHARACTER SET utf8mb4;
```

### 三步跑起来

```bash
# 1. 改数据库账号（默认 root/123456，或用环境变量覆盖）
#    MYSQL_USER=root MYSQL_PASSWORD=你的密码

# 2. 打包
mvn -DskipTests package

# 3. 启动（带模拟器，立刻就有数据）
java -jar target/iot-llm-platform-1.0.0.jar --iot.simulator.enabled=true
```

打开 <http://localhost:8080> ，用 `admin / 123456` 登录。

| 入口 | 地址 |
|---|---|
| 看板 | http://localhost:8080 |
| 接口文档 | http://localhost:8080/doc.html |
| MQTT | `tcp://localhost:1883` |

Windows 用户可以直接双击 `run.bat`（首次会自动打包）。

### 不想跑模拟器？

```bash
# 只起服务
java -jar target/iot-llm-platform-1.0.0.jar

# 用任意 MQTT 客户端手发一条（MQTTX / mosquitto_pub 都行）
# 用户名 = deviceKey，密码 = secret
mosquitto_pub -h localhost -p 1883 \
  -u dev_demo_gw01 -P a1b2c3d4e5f60718293a4b5c6d7e8f90 \
  -t 'iot/gw/dev_demo_gw01/telemetry' \
  -m '{"ts":1731657600000,"metrics":{"temperature":68.5,"humidity":45.2}}'
```

---

## 三、接口

登录拿 token 后，在 `doc.html` 右上角 Authorize 里填 `Bearer <token>` 即可调试。

| 分组 | 接口 | 权限 |
|---|---|---|
| 认证 | `POST /api/auth/login`、`GET /api/auth/me` | 公开 / 登录 |
| 设备 | `GET /api/devices`、`GET /api/devices/{id}`、`GET /api/devices/{id}/shadow` | USER |
| 设备 | `POST /api/devices`、`PUT /api/devices/{id}`、`DELETE /api/devices/{id}` | **ADMIN** |
| 遥测 | `GET /api/telemetry`、`/latest`、`/summary` | USER |
| 告警 | `GET /api/alarms`、`POST /api/alarms/{id}/ack`、`/close`、`/diagnose` | USER |
| 规则 | `GET /api/alarm-rules`、`POST`、`POST /{id}/toggle`、`DELETE` | **ADMIN** |
| 看板 | `GET /api/dashboard/overview` | USER |

两级 RBAC 的划分依据是"会不会改配置"：看数据和处置告警是值班人员的事（USER），改设备档案和告警规则是管理员的事（ADMIN）。

---

## 四、几个值得说的设计

### 1. 告警防抖：连续 N 点才开单

单个采样点越线就告警，运维第一天就会把整个系统静音——传感器毛刺、瞬时冲击、采样抖动都会触发。所以规则里带 `durationPoints`：**连续 N 个点都越线**才真正开单，中间任何一个点回落就清零重数。

`AlarmEngine` 把每个 `(deviceId, ruleId)` 的连续计数放在 Caffeine 里。它是瞬时状态，重启丢掉可以接受：重新数 N 个点即可，不会漏报，只是晚几个采样周期。

### 2. 设备影子：把"现在"和"过去"分开

时序表回答"过去发生了什么"，设备影子回答"现在是什么状态"。

看板要显示实时值，如果每次都去 `telemetry` 做 `GROUP BY metric ORDER BY ts DESC`，数据量一大就是全表扫。所以上报时顺手把最新值塞进 Caffeine，读的时候 O(1)。影子还兼任 AI 诊断的上下文——模型需要知道设备当前各指标是多少，而不是只知道触发那一个。

### 3. 一机一密 + 主题归属校验

设备连 MQTT 时 `username = deviceKey`、`password = secret`，`DeviceAuthenticator` 查缓存比对，对不上直接拒绝 CONNECT。

但只做认证不够：设备 A 认证通过后，如果知道设备 B 的 deviceKey，仍然可以往 `iot/gw/dev_B/telemetry` 发假数据。所以 `TelemetryInterceptor` 里加了第二道校验——**主题里的 deviceKey 必须等于当前连接的 username**。这道校验 Broker 不会替你做，ACL 只管"能不能发这个主题"。

### 4. 大模型降级：三条路径都留了后手

| 情况 | 行为 |
|---|---|
| 没配 API Key（默认） | 走 `MockDiagnoser` 本地规则，输出同样的四段结构 |
| 配了 Key 且 `iot.ai.provider=dashscope` | 调通义千问，虚拟线程 + 12 秒超时 |
| 调用超时 / 报错 | 捕获后回落本地规则，并在结果里标 `fallback=true` 和模型名 |

**为什么必须有降级**：别人 clone 下来没有你的 API Key，如果强依赖模型，项目直接跑不起来，价值归零。降级不是"容错"，是"让别人能验证"。

### 5. 告警和诊断用事件解耦

告警落库是同步的、必须快；诊断是异步的、可以慢、可以失败。所以 `AlarmEngine` 只负责 `publishEvent`，`AlarmDiagnosisListener` 用 `@TransactionalEventListener(AFTER_COMMIT) + @Async` 消费。

用 `AFTER_COMMIT` 而不是普通 `@EventListener`：异步线程去查库时，事务可能还没提交，会报"告警不存在"——这是异步场景最常踩的坑。

---

## 五、目录

```
src/main/java/com/iot/platform/
├── ai/            诊断上下文、Mock 与 DashScope 两个实现、门面（选路/超时/降级）
├── common/        Result、分页、业务异常、全局异常、traceId
├── config/        Security、Knife4j、缓存、线程池、演示数据初始化
├── controller/    认证 / 设备 / 遥测 / 告警 / 规则 / 看板
├── dto/           请求响应对象（record）
├── entity/        5 张表的映射
├── event/         告警事件 + 异步诊断监听
├── mapper/        MyBatis-Plus Mapper
├── mqtt/          内嵌 Broker、设备认证、PUBLISH 拦截、入库服务
├── security/      JWT 签发解析、认证过滤器
├── service/       设备 / 遥测 / 告警 / 规则 / 影子 / 健康检查
└── simulator/     设备模拟器（真连 MQTT，不是塞库）
```

表结构见 `src/main/resources/db/schema.sql`，随应用启动自动执行（幂等）。

---

## 六、测试

```bash
mvn test
```

17 个用例，覆盖四块最容易写错的地方：

- **告警防抖**：连续 3 点才触发、中途回落清零、非规则指标不参与
- **设备凭据**：正确 / 错误 / 设备不存在 / 已停用 / 空密码
- **登录**：签发 token、密码错误、账号不存在返回同一句、停用账号
- **诊断器**：四段结构、趋势识别、未知指标兜底、斜率计算

---

## 七、边界（写清楚比藏着好）

| 限制 | 原因 | 真要做怎么改 |
|---|---|---|
| Moquette 单机、无集群 | 选它是为了"零依赖可运行" | 生产换 EMQX / HiveMQ，`MqttBrokerServer` 整个删掉即可，回调逻辑不用动 |
| 遥测不做降采样/分区 | 演示数据量小 | 上规模后按天分表或转 ClickHouse / TDengine |
| 告警计数在内存 | 重启丢计数 | 多实例部署时换成 Redis |
| 没有前端构建链 | 单页足够演示 | 要工程化就换 Vue3 + Vite |
| API Key 走配置文件 | 演示方便 | 生产用 KMS 或环境变量注入，别提交到仓库 |

---

## 八、License

MIT
