package com.smartdialer.provider;

import com.smartdialer.enums.TelecomProviderType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class TelecomProviderRegistry {

    private final Map<TelecomProviderType, TelecomProvider> providers = new EnumMap<>(TelecomProviderType.class);
    private volatile TelecomProviderType activeProviderType = TelecomProviderType.MOCK_A;

    public TelecomProviderRegistry(List<TelecomProvider> providerList) {
        for (TelecomProvider provider : providerList) {
            providers.put(provider.getProviderType(), provider);
        }
    }

    public TelecomProvider getProvider(TelecomProviderType type) {
        TelecomProvider provider = providers.get(type);
        if (provider == null) {
            throw new IllegalArgumentException("Unknown telecom provider type: " + type);
        }
        return provider;
    }

    public TelecomProvider getActiveProvider() {
        TelecomProvider provider = providers.get(activeProviderType);
        if (provider == null || !provider.getProviderStatus().operational()) {
            // Fallback to MOCK_A if available
            return providers.getOrDefault(TelecomProviderType.MOCK_A, provider);
        }
        return provider;
    }

    public void setActiveProviderType(TelecomProviderType activeProviderType) {
        this.activeProviderType = activeProviderType;
    }

    public TelecomProviderType getActiveProviderType() {
        return activeProviderType;
    }

    public Map<TelecomProviderType, TelecomProvider> getAllProviders() {
        return Map.copyOf(providers);
    }
}
