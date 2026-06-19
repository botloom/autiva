package cn.bitloom.util;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class SpringContextUtil implements ApplicationContextAware {

    private static ApplicationContext context;

    @Override
    public void setApplicationContext(@NotNull ApplicationContext applicationContext) throws BeansException {
        SpringContextUtil.context = applicationContext;
    }

    /**
     * Gets bean.
     *
     * @param <T>   the type parameter
     * @param clazz the clazz
     * @return the bean
     */
    public static <T> T getBean(Class<T> clazz) {
        return context.getBean(clazz);
    }

    /**
     * Gets bean.
     *
     * @param name the name
     * @return the bean
     */
    public static Object getBean(String name) {
        return context.getBean(name);
    }

    /**
     * Gets bean.
     *
     * @param <T>   the type parameter
     * @param name  the name
     * @param clazz the clazz
     * @return the bean
     */
    public static <T> T getBean(String name, Class<T> clazz) {
        return context.getBean(name, clazz);
    }
}