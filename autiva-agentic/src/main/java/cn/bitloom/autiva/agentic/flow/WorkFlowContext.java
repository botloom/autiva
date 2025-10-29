package cn.bitloom.autiva.agentic.flow;

import lombok.Getter;
import lombok.Setter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The type Work flow context.
 *
 * @author ningyu
 */
public class WorkFlowContext {

    @Setter
    @Getter
    private String messageId;
    @Setter
    @Getter
    private String conversationId;
    private final Map<String, Object> params;
    private FluxSink<String> messageSink;
    private FluxSink<String> historySink;

    /**
     * The type Type reference.
     *
     * @param <T> the type parameter
     */
    @Getter
    public abstract static class TypeReference<T> {

        private final Type type;

        /**
         * Instantiates a new Type reference.
         */
        protected TypeReference() {
            Type superClass = getClass().getGenericSuperclass();
            if (superClass instanceof ParameterizedType) {
                this.type = ((ParameterizedType) superClass).getActualTypeArguments()[0];
            } else {
                throw new IllegalArgumentException("TypeReference must be parameterized");
            }
        }

    }


    /**
     * Instantiates a new Work flow context.
     */
    public WorkFlowContext() {
        this.params = new ConcurrentHashMap<>();
        Flux<String> messageFlux = Flux.create(sink -> this.messageSink = sink);
        // 订阅flux
        messageFlux.subscribe(
        );
        Flux<String> historyFlux = Flux.create(sink -> this.historySink = sink);
        // 订阅flux
        historyFlux.subscribe(
                history -> {

                }
        );
    }

    /**
     * Emit.
     *
     * @param response the response
     */
    public void emitMessage(String response) {
        messageSink.next(response);
    }

    /**
     * Emit history.
     *
     * @param history the history
     */
    public void emitHistory(String history) {
        historySink.next(history);
    }

    /**
     * Put.
     *
     * @param key   the key
     * @param value the value
     */
    public void putParam(String key, Object value) {
        this.params.put(key, value);
    }

    /**
     * 数据含有泛型参数时使用该方法
     *
     * @param <T>           the type parameter
     * @param key           the key
     * @param typeReference the type reference
     * @return the t
     */
    @SuppressWarnings("unchecked")
    public <T> T getParam(String key, TypeReference<T> typeReference) {
        return (T) (this.params.get(key));
    }

    /**
     * Get t.
     *
     * @param <T>   the type parameter
     * @param key   the key
     * @param clazz the clazz
     * @return the t
     */
    public <T> T getParam(String key, Class<T> clazz) {
        return clazz.cast(this.params.get(key));
    }

    /**
     * Clear.
     */
    public void clear() {
        this.params.clear();
        messageSink.complete();
        historySink.complete();
    }

}
