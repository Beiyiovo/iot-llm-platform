package com.iot.platform.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iot.platform.entity.AlarmRule;
import com.iot.platform.entity.Device;
import com.iot.platform.entity.SysUser;
import com.iot.platform.mapper.AlarmRuleMapper;
import com.iot.platform.mapper.DeviceMapper;
import com.iot.platform.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 首次启动时灌演示数据。幂等：已经有数据就跳过，不会重复插。
 *
 * <p>设备的 deviceKey / secret 故意写成固定值，这样模拟器和 README 里的 curl 命令
 * 不用改就能跑，别人 clone 下来照抄即可。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final SysUserMapper userMapper;
    private final DeviceMapper deviceMapper;
    private final AlarmRuleMapper ruleMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        initUsers();
        initDevices();
    }

    private void initUsers() {
        if (userMapper.selectCount(null) > 0) {
            return;
        }
        userMapper.insert(user("admin", "123456", "管理员", "ADMIN"));
        userMapper.insert(user("user", "123456", "运维值班", "USER"));
        log.info("已创建演示账号：admin/123456（ADMIN）、user/123456（USER）");
    }

    private SysUser user(String username, String rawPassword, String nickname, String role) {
        SysUser u = new SysUser();
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode(rawPassword));
        u.setNickname(nickname);
        u.setRole(role);
        u.setEnabled(1);
        return u;
    }

    private void initDevices() {
        if (deviceMapper.selectCount(null) > 0) {
            return;
        }
        Device gateway = device("gw", "dev_demo_gw01", "a1b2c3d4e5f60718293a4b5c6d7e8f90",
                "3号楼网关", "3号楼 2 层弱电井");
        Device cold = device("cold", "dev_demo_cold01", "b2c3d4e5f60718293a4b5c6d7e8f90a1",
                "冷库温控", "1号冷库 B 区");
        Device power = device("power", "dev_demo_power01", "c3d4e5f60718293a4b5c6d7e8f90a1b2",
                "车间配电柜", "2号车间东侧");

        List.of(gateway, cold, power).forEach(deviceMapper::insert);

        ruleMapper.insert(rule(gateway.getId(), "temperature", "GT", 60.0, 3, "CRITICAL"));
        ruleMapper.insert(rule(gateway.getId(), "humidity", "LT", 20.0, 3, "WARN"));
        ruleMapper.insert(rule(cold.getId(), "temperature", "GT", -15.0, 2, "CRITICAL"));
        ruleMapper.insert(rule(power.getId(), "current", "GT", 120.0, 3, "WARN"));
        ruleMapper.insert(rule(power.getId(), "temperature", "GT", 70.0, 3, "WARN"));

        log.info("已创建设备 {} 台、告警规则 5 条。deviceKey: {} / {} / {}",
                List.of(gateway, cold, power).size(),
                gateway.getDeviceKey(), cold.getDeviceKey(), power.getDeviceKey());
    }

    private Device device(String productKey, String deviceKey, String secret, String name, String location) {
        Device d = new Device();
        d.setProductKey(productKey);
        d.setDeviceKey(deviceKey);
        d.setSecret(secret);
        d.setName(name);
        d.setLocation(location);
        d.setStatus("OFFLINE");
        d.setDeleted(0);
        return d;
    }

    private AlarmRule rule(Long deviceId, String metric, String op, double threshold, int points, String level) {
        AlarmRule r = new AlarmRule();
        r.setDeviceId(deviceId);
        r.setMetric(metric);
        r.setOperator(op);
        r.setThreshold(threshold);
        r.setDurationPoints(points);
        r.setLevel(level);
        r.setEnabled(1);
        return r;
    }
}
