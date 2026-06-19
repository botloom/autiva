package cn.bitloom.pet;

import lombok.Getter;

/**
 * 植物类型枚举，根据用户聊天风格映射不同的植物。
 */
@Getter
public enum PetType {

    SUNFLOWER("向日葵", "活泼、emoji多、轻松"),
    CACTUS("仙人掌", "简洁、技术向、代码多"),
    IVY("常春藤", "创意、长消息、话题广"),
    BAMBOO("竹子", "高频、短消息、高效"),
    ROSE("玫瑰", "情感丰富、表达多样"),
    BONSAI("盆景", "深思、哲学、长对话");

    private final String label;
    private final String styleDesc;

    PetType(String label, String styleDesc) {
        this.label = label;
        this.styleDesc = styleDesc;
    }

}
