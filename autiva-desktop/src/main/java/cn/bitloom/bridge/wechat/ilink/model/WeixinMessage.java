package cn.bitloom.bridge.wechat.ilink.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class WeixinMessage {

    @JsonProperty("message_id")
    private Long messageId;

    @JsonProperty("from_user_id")
    private String fromUserId;

    @JsonProperty("to_user_id")
    private String toUserId;

    @JsonProperty("create_time_ms")
    private Long createTimeMs;

    @JsonProperty("context_token")
    private String contextToken;

    @JsonProperty("item_list")
    private List<MessageItem> itemList;
}
