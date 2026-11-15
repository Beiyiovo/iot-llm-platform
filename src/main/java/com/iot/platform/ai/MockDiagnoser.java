package com.iot.platform.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地规则诊断器。
 *
 * <p>它不是"假的 AI"，是一层保底：没有 API Key、断网、模型超时的时候，
 * 平台仍然要给运维一个能看的东西。同时也是对比基线——模型输出跑偏时，
 * 拿它的结论一对照就知道该不该信。
 *
 * <p>规则由三部分组成：指标语义（温度/湿度/CO2…）× 越限方向（偏高/偏低）× 趋势（突升/缓升）。
 */
@Slf4j
@Component
public class MockDiagnoser implements Diagnoser {

    @Override
    public String name() {
        return "rule-based-mock";
    }

    @Override
    public DiagnosisResult diagnose(DiagnosisContext ctx) {
        long start = System.currentTimeMillis();
        boolean rising = ctx.slope() > 0;
        boolean sudden = Math.abs(ctx.slope()) > Math.abs(ctx.threshold()) * 0.05;
        boolean high = ctx.value() > ctx.threshold();

        MetricProfile profile = KNOWLEDGE.getOrDefault(ctx.metric().toLowerCase(), GENERIC);
        List<String> causes = high ? profile.highCauses : profile.lowCauses;
        List<String> actions = high ? profile.highActions : profile.lowActions;

        StringBuilder sb = new StringBuilder();
        sb.append("【根因判断】\n");
        sb.append(String.format("%s 当前 %.2f，%s阈值 %.2f 约 %.1f%%，",
                ctx.metric(), ctx.value(), ctx.operatorText(), ctx.threshold(), ctx.exceedPercent()));
        if (sudden) {
            sb.append(rising ? "且近几个采样点仍在快速上升，" : "且仍在快速下降，");
            sb.append("判断为").append(profile.subject).append("正在持续恶化，不是单点毛刺。\n");
        } else if (rising) {
            sb.append("近几个采样点缓慢上行，判断为").append(profile.subject).append("逐步劣化，尚未失控。\n");
        } else {
            sb.append("数值已趋稳，判断为").append(profile.subject).append("维持在高位，需人工确认是否已到临界。\n");
        }

        sb.append("\n【可能原因】\n");
        for (int i = 0; i < causes.size(); i++) {
            sb.append(i + 1).append(". ").append(causes.get(i)).append('\n');
        }

        sb.append("\n【处置建议】\n");
        for (int i = 0; i < actions.size(); i++) {
            sb.append(i + 1).append(". ").append(actions.get(i)).append('\n');
        }

        sb.append("\n【风险提示】\n");
        if ("CRITICAL".equals(ctx.level())) {
            sb.append("级别 CRITICAL，若 30 分钟内没有回落趋势，建议现场介入并准备降载/停机。");
        } else if (!ctx.recentAlarms().isEmpty()) {
            sb.append("该设备近 24 小时已产生 ").append(ctx.recentAlarms().size())
                    .append(" 条其他告警，存在多指标联动的可能，建议一并排查。");
        } else {
            sb.append("暂未观察到联动告警，可先按常规巡检流程处理。");
        }

        return new DiagnosisResult(sb.toString(), name(), false, System.currentTimeMillis() - start);
    }

    private record MetricProfile(String subject,
                                 List<String> highCauses, List<String> highActions,
                                 List<String> lowCauses, List<String> lowActions) {
    }

    private static final MetricProfile GENERIC = new MetricProfile("该指标",
            List.of("传感器读数漂移，标定失效", "采集线路接触不良导致异常值", "真实工况超出设计范围"),
            List.of("用标准仪器复核读数", "检查探头接线与接地", "核对设计工况上限，评估是否需要扩容"),
            List.of("传感器掉线或供电不足", "采集线路断路", "真实工况低于设计下限"),
            List.of("确认设备是否在线、供电是否正常", "检查信号线与接插件", "核对设计工况下限"));

    private static final Map<String, MetricProfile> KNOWLEDGE = new LinkedHashMap<>();

    static {
        KNOWLEDGE.put("temperature", new MetricProfile("散热系统",
                List.of("散热风道堵塞或风扇转速下降（与持续升温特征最吻合）",
                        "设备负载突增，功耗与发热同步上升",
                        "机柜进风侧热回流，环境温度偏高",
                        "测温探头贴装松动，读数偏高"),
                List.of("检查风扇转速与滤网积灰，必要时停机清灰",
                        "对比同机柜其他设备温度，排除环境因素",
                        "用点温枪核对探头读数，排除传感器漂移",
                        "若仍上行，先降载再排查"),
                List.of("环境温度过低或设备已停机",
                        "加热模块失效（户外机柜）",
                        "温度探头断线，读数为默认值"),
                List.of("确认设备运行状态与加热模块供电",
                        "检查探头接线",
                        "户外机柜确认伴热是否工作")));

        KNOWLEDGE.put("humidity", new MetricProfile("环境湿度",
                List.of("机柜密封失效，外部湿气渗入",
                        "除湿模块未启动或排水堵塞",
                        "附近有水源渗漏"),
                List.of("检查机柜密封条与进线孔封堵",
                        "确认除湿机运行状态与排水管",
                        "排查机房漏水点"),
                List.of("密封过严且无加湿，静电风险上升",
                        "除湿过度",
                        "湿度探头失效"),
                List.of("评估是否需要加湿或增加防静电措施",
                        "调整除湿阈值",
                        "复核探头")));

        KNOWLEDGE.put("co2", new MetricProfile("空气质量",
                List.of("通风系统风量不足或新风阀未开",
                        "空间内人员密度超出设计值",
                        "CO2 传感器需要重新标定"),
                List.of("检查新风机组与风阀开度",
                        "核对在场人数与设计新风量",
                        "用标准气体标定传感器"),
                List.of("通风过量，能耗浪费", "传感器故障"),
                List.of("复核新风量设定", "检查传感器")));

        KNOWLEDGE.put("vibration", new MetricProfile("机械振动",
                List.of("轴承磨损或润滑失效",
                        "转子动平衡破坏",
                        "地脚螺栓松动，共振放大"),
                List.of("测轴承部位温度与噪声，判断是否需更换",
                        "做动平衡检测",
                        "紧固地脚并复查振动频谱"),
                List.of("设备停机", "传感器脱落"),
                List.of("确认设备运行状态", "检查传感器安装")));

        KNOWLEDGE.put("pressure", new MetricProfile("管路压力",
                List.of("管路或接头泄漏",
                        "泵/压缩机效率下降",
                        "安全阀误动作"),
                List.of("分段保压查漏",
                        "检查泵运行电流与出口流量",
                        "校验安全阀整定值"),
                List.of("管路堵塞", "泵未启动", "压力传感器零点漂移"),
                List.of("检查过滤器压差", "确认泵运行状态", "重新标定传感器")));

        KNOWLEDGE.put("voltage", new MetricProfile("供电电压",
                List.of("市电波动或变压器分接头异常",
                        "大功率负载启停造成瞬时压降",
                        "线路接触电阻增大"),
                List.of("记录电压波动曲线，判断是否与负载启停同步",
                        "检查配电柜接线端子温升",
                        "必要时加装稳压装置"),
                List.of("后端负载突增", "线路压降过大", "采样回路异常"),
                List.of("核算负载与线径", "测量末端电压", "检查采样回路")));

        KNOWLEDGE.put("current", new MetricProfile("负载电流",
                List.of("负载侧短路或绝缘下降",
                        "电机堵转/机械卡涩",
                        "三相不平衡"),
                List.of("立即测量绝缘电阻，必要时断电",
                        "检查机械传动是否卡涩",
                        "测量三相电流平衡度"),
                List.of("负载断开或接触器未吸合", "电流互感器故障"),
                List.of("确认接触器与回路通断", "检查互感器二次侧")));
    }
}
