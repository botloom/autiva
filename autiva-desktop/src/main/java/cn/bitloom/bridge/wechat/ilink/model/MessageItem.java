package cn.bitloom.bridge.wechat.ilink.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class MessageItem {

    private Integer type;

    @JsonProperty("text_item")
    private TextItem textItem;

    @JsonProperty("image_item")
    private ImageItem imageItem;

    @JsonProperty("file_item")
    private FileItem fileItem;

    @JsonProperty("voice_item")
    private VoiceItem voiceItem;

    @JsonProperty("video_item")
    private VideoItem videoItem;
}
