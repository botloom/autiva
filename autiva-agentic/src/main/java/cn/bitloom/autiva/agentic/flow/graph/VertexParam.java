package cn.bitloom.autiva.agentic.flow.graph;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.Getter;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Set;

/**
 * The type Vertex param.
 *
 * @author ningyu
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VertexParam {
    private String systemPrompt;
    private Integer retryCount;
    private Set<String> toolSet;
    private Set<String> advisorSet;
    private Map<String, Object> extraParams;

    /**
     * Gets extra param.
     *
     * @param <T>   the type parameter
     * @param key   the key
     * @param clazz the clazz
     * @return the extra param
     */
    public <T> T getExtraParam(String key, Class<T> clazz) {
        return clazz.cast(this.extraParams.get(key));
    }

    /**
     * Gets extra param.
     *
     * @param <T>           the type parameter
     * @param key           the key
     * @param typeReference the type reference
     * @return the extra param
     */
    @SuppressWarnings("unchecked")
    public <T> T getExtraParam(String key, TypeReference<T> typeReference) {
        return (T) (this.extraParams.get(key));
    }

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

}
