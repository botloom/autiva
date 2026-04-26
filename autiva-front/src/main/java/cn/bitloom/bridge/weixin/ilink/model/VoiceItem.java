package cn.bitloom.bridge.weixin.ilink.model;

import lombok.Data;

@Data
public class VoiceItem {
    private Object media;
    private Integer encodeType;
    private Integer playtime;
    private Integer sampleRate;
}
