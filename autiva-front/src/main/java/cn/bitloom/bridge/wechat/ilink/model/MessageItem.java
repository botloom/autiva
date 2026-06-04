package cn.bitloom.bridge.wechat.ilink.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

@Data
public class MessageItem {

    private Integer type;

    @JSONField(name = "text_item")
    private TextItem textItem;

    @JSONField(name = "image_item")
    private ImageItem imageItem;

    @JSONField(name = "file_item")
    private FileItem fileItem;

    @JSONField(name = "voice_item")
    private VoiceItem voiceItem;

    @JSONField(name = "video_item")
    private VideoItem videoItem;
}
