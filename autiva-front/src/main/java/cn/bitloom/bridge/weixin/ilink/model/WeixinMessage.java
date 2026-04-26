package cn.bitloom.bridge.weixin.ilink.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.util.List;

@Data
public class WeixinMessage {

    @JSONField(name = "message_id")
    private Long messageId;

    @JSONField(name = "from_user_id")
    private String fromUserId;

    @JSONField(name = "to_user_id")
    private String toUserId;

    @JSONField(name = "create_time_ms")
    private Long createTimeMs;

    @JSONField(name = "context_token")
    private String contextToken;

    @JSONField(name = "item_list")
    private List<MessageItem> itemList;
}
