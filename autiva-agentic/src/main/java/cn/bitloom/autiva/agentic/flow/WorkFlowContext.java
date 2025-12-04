package cn.bitloom.autiva.agentic.flow;


import lombok.Getter;
import lombok.Setter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

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
     * The Running.
     */
    protected final AtomicBoolean running = new AtomicBoolean(true);

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
        Flux<String> historyFlux = Flux.create(sink -> this.historySink = sink);
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
     * Param as map map.
     *
     * @return the map
     */
    public Map<String, Object> paramAsMap() {
        return this.params;
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
