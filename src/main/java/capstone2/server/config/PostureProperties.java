package capstone2.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "posture")
@Data
public class PostureProperties {

    private TrunkLean trunkLean = new TrunkLean();
    private KneeFlexion kneeFlexion = new KneeFlexion();
    private Llm llm = new Llm();

    @Data
    public static class TrunkLean {
        private double optimalMin;
        private double optimalMax;
        private double suboptimalMin;
        private double unreliableMin;
        private double unreliableMax;
        private double stdVal;
        private int refMin;
        private int refMax;
        private double sensitivity;
    }

    @Data
    public static class KneeFlexion {
        private double highRiskMax;
        private double cautionLowMax;
        private double optimalMin;
        private double optimalMax;
        private double cautionHighMax;
        private double suboptimalMin;
        private double unreliableMin;
        private double stdVal;
        private int refMin;
        private int refMax;
        private double sensitivity;
    }

    @Data
    public static class Llm {
        private long timeoutSeconds = 60;
    }
}
