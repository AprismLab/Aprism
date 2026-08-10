package com.aprism.loader;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Logger;

import com.aprism.api.ai.AiAssistant;
import com.aprism.api.AprismEventBus;
import com.aprism.api.rendering.RenderingProvider;
import com.aprism.api.AprismRegistry;
import com.aprism.api.ExtensionContainer;
import com.aprism.api.ExtensionContext;
import com.aprism.loader.loaderext.LoaderEntrypointHandler;
import com.aprism.loader.loaderext.LoaderEntrypointRegistry;

/**
 * Extension-scoped {@link ExtensionContext} implementation. Each loaded
 * extension receives its own context instance bound to its container, the
 * shared event bus and registry, a logger named after the extension id, and
 * a callback for registering loader-support folders at runtime.
 *
 * @author BlockConnect@StarsailsClover
 */
public final class ExtensionContextImpl implements ExtensionContext {

    private final ExtensionContainer extension;
    private final AprismEventBus eventBus;
    private final AprismRegistry registry;
    private final Logger logger;
    private final BiConsumer<String, String> loaderSupportRegistrar;
    private final Consumer<Object> aiAssistantRegistrar;
    private final Consumer<Object> renderingProviderRegistrar;

    /**
     * @param extension            the owning extension container
     * @param eventBus             the shared event bus
     * @param registry             the shared registry
     * @param loaderSupportRegistrar callback to register a loader-support folder
     */
    public ExtensionContextImpl(ExtensionContainer extension, AprismEventBus eventBus,
            AprismRegistry registry, BiConsumer<String, String> loaderSupportRegistrar) {
        this(extension, eventBus, registry, loaderSupportRegistrar, assistant -> {
            throw new UnsupportedOperationException(
                    "AI assistant registration is not supported in this context");
        }, provider -> {
            throw new UnsupportedOperationException(
                    "rendering provider registration is not supported in this context");
        });
    }

    /**
     * @param extension              the owning extension container
     * @param eventBus               the shared event bus
     * @param registry               the shared registry
     * @param loaderSupportRegistrar callback to register a loader-support folder
     * @param aiAssistantRegistrar   callback to register an AI assistant
     *                               (v26.3-Alpha.4, goal #8; experimental)
     */
    public ExtensionContextImpl(ExtensionContainer extension, AprismEventBus eventBus,
            AprismRegistry registry, BiConsumer<String, String> loaderSupportRegistrar,
            Consumer<Object> aiAssistantRegistrar) {
        this(extension, eventBus, registry, loaderSupportRegistrar, aiAssistantRegistrar,
                provider -> {
                    throw new UnsupportedOperationException(
                            "rendering provider registration is not supported in this context");
                });
    }

    /**
     * @param extension              the owning extension container
     * @param eventBus               the shared event bus
     * @param registry               the shared registry
     * @param loaderSupportRegistrar callback to register a loader-support folder
     * @param aiAssistantRegistrar   callback to register an AI assistant
     * @param renderingProviderRegistrar callback to register a rendering
     *                               provider (v26.3-Alpha.5, goal #9;
     *                               experimental)
     */
    public ExtensionContextImpl(ExtensionContainer extension, AprismEventBus eventBus,
            AprismRegistry registry, BiConsumer<String, String> loaderSupportRegistrar,
            Consumer<Object> aiAssistantRegistrar,
            Consumer<Object> renderingProviderRegistrar) {
        this.extension = extension;
        this.eventBus = eventBus;
        this.registry = registry;
        this.logger = Logger.getLogger("aprism.ext." + extension.getExtensionId());
        this.loaderSupportRegistrar = loaderSupportRegistrar;
        this.aiAssistantRegistrar = aiAssistantRegistrar;
        this.renderingProviderRegistrar = renderingProviderRegistrar;
    }

    @Override
    public ExtensionContainer getExtension() {
        return extension;
    }

    @Override
    public AprismEventBus getEventBus() {
        return eventBus;
    }

    @Override
    public AprismRegistry getRegistry() {
        return registry;
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public void registerLoaderSupport(String loaderKey, String modFolder) {
        if (loaderKey == null || loaderKey.isBlank()) {
            throw new IllegalArgumentException("loaderKey must not be blank");
        }
        if (modFolder == null || modFolder.isBlank()) {
            throw new IllegalArgumentException("modFolder must not be blank");
        }
        loaderSupportRegistrar.accept(loaderKey, modFolder);
    }

    @Override
    public void registerRenderingProvider(Object provider) {
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        if (!(provider instanceof RenderingProvider)) {
            throw new IllegalArgumentException(
                    "provider must implement RenderingProvider, got "
                            + provider.getClass().getName());
        }
        renderingProviderRegistrar.accept(provider);
    }

    @Override
    public void registerAiAssistant(Object assistant) {
        if (assistant == null) {
            throw new IllegalArgumentException("assistant must not be null");
        }
        if (!(assistant instanceof AiAssistant)) {
            throw new IllegalArgumentException(
                    "assistant must implement AiAssistant, got "
                            + assistant.getClass().getName());
        }
        aiAssistantRegistrar.accept(assistant);
    }

    @Override
    public void registerEntrypointHandler(String loaderKey, Object handler) {
        if (loaderKey == null || loaderKey.isBlank()) {
            throw new IllegalArgumentException("loaderKey must not be blank");
        }
        if (handler == null) {
            throw new IllegalArgumentException("handler must not be null");
        }
        if (!(handler instanceof LoaderEntrypointHandler typed)) {
            throw new IllegalArgumentException(
                    "handler must implement LoaderEntrypointHandler, got "
                            + handler.getClass().getName());
        }
        if (!loaderKey.equals(typed.loaderKey())) {
            throw new IllegalArgumentException(
                    "handler.loaderKey() (" + typed.loaderKey()
                            + ") does not match registration key (" + loaderKey + ")");
        }
        LoaderEntrypointRegistry.register(typed);
    }
}
