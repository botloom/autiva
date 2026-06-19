package cn.bitloom.holder;

import cn.bitloom.constant.AppConstants;
import javafx.stage.StageStyle;

public interface DialogHolder {

    default double getWidth() {
        return AppConstants.Stage.WIDTH;
    }

    default double getHeight() {
        return AppConstants.Stage.HEIGHT;
    }

    default boolean isResizable() {
        return false;
    }

    default StageStyle getStageStyle() {
        return StageStyle.UNIFIED;
    }
}
