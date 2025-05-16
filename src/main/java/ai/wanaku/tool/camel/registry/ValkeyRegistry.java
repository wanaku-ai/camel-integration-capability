package ai.wanaku.tool.camel.registry;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import ai.wanaku.api.types.management.Configuration;
import ai.wanaku.api.types.management.Configurations;
import ai.wanaku.api.types.management.Service;
import ai.wanaku.api.types.management.State;
import io.valkey.Jedis;
import io.valkey.JedisPool;
import io.valkey.JedisPoolConfig;
import io.valkey.StreamEntryID;
import io.valkey.params.XAddParams;
import io.valkey.resps.StreamEntry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.LoggerFactory;

/**
 * Host information is stored as keys in a hashmap in Valkey. Suppose, for instance, a service
 * named "ftp". It would contain keys such as:
 * <p>
 * wanaku-target-address -> ip:port
 * wanaku-target-type -> tool-invoker
 * <p>
 * And also the configurations for the service as a hashmap
 * config1 -> description1, config2 -> description2, etc.
 * <p>
 * This class is basically iterating over the hashmap
 */
public class ValkeyRegistry {
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(ValkeyRegistry.class);

    private final JedisPool jedisPool;

    public ValkeyRegistry(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    /**
     * In case of a deregister event is not triggered (for example, kill -9),
     * The entry in Valkey will be removed after expireTime.
     * <p>
     * NB {wanaku.service.expire-time} > {wanaku.service.provider.registration.interval}
     */
    @ConfigProperty(name = "wanaku.service.expire-time", defaultValue = "60")
    int expireTime;

    /**
     * Registers a new service with the given configurations.
     *
     * @param serviceTarget The service target, including its address and type.
     * @param configurations A map of configuration key-value pairs for the service.
     */
    public void register(ServiceTarget serviceTarget, Map<String, String> configurations) {
        try (io.valkey.Jedis jedis = jedisPool.getResource()) {
            // Register the service on the specific set
            String serviceKey = ReservedKeys.getServiceKey(serviceTarget.getServiceType());
            jedis.sadd(serviceKey, serviceTarget.getService());

            jedis.hset(serviceTarget.getService(), ReservedKeys.WANAKU_TARGET_ADDRESS, serviceTarget.toAddress());
            jedis.hset(serviceTarget.getService(), ReservedKeys.WANAKU_TARGET_TYPE, serviceTarget.getServiceType().asValue());

            LOG.debug("Service {} with target {} registered", serviceTarget.getService(), serviceTarget.toAddress());

            for (var entry : configurations.entrySet()) {
                if (!jedis.hexists(serviceTarget.getService(), entry.getKey())) {
                    LOG.info("Registering configuration {} for service {}", entry.getKey(), serviceTarget.getService());
                    Configuration configuration = new Configuration();
                    configuration.setDescription(entry.getValue());
                    jedis.hset(serviceTarget.getService(), entry.getKey(), configuration.toJson());
                } else {
                    LOG.info("A configuration {} for service {} already exists", entry.getKey(), serviceTarget.getService());
                }
            }

//            jedis.expire(serviceTarget.getService(), expireTime);
            // Need to wait for https://github.com/valkey-io/valkey/issues/640
            // jedis.expireMember(serviceKey, serviceTarget.getService(), EXPIRE_TIME);
        } catch (Exception e) {
            LOG.error("Failed to register service {}: {}", serviceTarget.getService(), e.getMessage());
        }
    }

    /**
     * Deregisters a service with the given name.
     *
     * @param service The name of the service to deregister.
     * @param serviceType the type of service to deregister
     */
    public void deregister(String service, ServiceType serviceType) {
        try (io.valkey.Jedis jedis = jedisPool.getResource()) {
            String serviceKey = ReservedKeys.getServiceKey(serviceType);
            jedis.srem(serviceKey, service);

            LOG.info("Service {} registered", service);
        } catch (Exception e) {
            LOG.error("Failed to register service {}: {}", service, e.getMessage(), e);
        }
    }


    public void saveState(String service, boolean healthy, String message) {
        try (io.valkey.Jedis jedis = jedisPool.getResource()) {
            Map<String, String> state = Map.of("service", service, "healthy",
                    Boolean.toString(healthy), "message", (healthy ? "healthy" : message));

            jedis.xadd(stateKey(service), state, XAddParams.xAddParams());
        } catch (Exception e) {
            LOG.error("Failed to save state for {}: {}", service, e.getMessage(), e);
        }
    }

    public List<State> getState(String service, int count) {
        try (io.valkey.Jedis jedis = jedisPool.getResource()) {

            String stateKey = stateKey(service);
            Instant now = Instant.now();
            long endEpoch = now.toEpochMilli();
            long startEpoch = now.minusSeconds(60).toEpochMilli();

            List<StreamEntry> streamEntries = jedis.xrange(stateKey, new StreamEntryID(startEpoch), new StreamEntryID(endEpoch));

            List<State> states = new ArrayList<>(streamEntries.size());

            for (StreamEntry streamEntry : streamEntries) {
                LOG.debug("Entry {}", streamEntry);

                Map<String, String> fields = streamEntry.getFields();
                String serviceName = fields.get("service");
                String message = fields.get("message");
                String healthy = fields.get("healthy");

                State state = new State(serviceName, Boolean.parseBoolean(healthy), message);
                states.add(state);
            }

            return states;
        } catch (Exception e) {
            LOG.error("Failed to get state for {}: {}", service, e.getMessage(), e);
        }

        return List.of();
    }

    private static String stateKey(String service) {
        return "state:" + service;
    }

    /**
     * Retrieves a service with the given name.
     *
     * @param service The name of the service to retrieve.
     * @return A Service object representing the retrieved service.
     */
    public Service getService(String service) {
        try (io.valkey.Jedis jedis = jedisPool.getResource()) {
            return newService(jedis, service);
        }
    }

    /**
     * Retrieves a map of services with the given type.
     *
     * @param serviceType The type of services to retrieve.
     * @return A map of Service objects representing the retrieved services.
     */
    public Map<String, Service> getEntries(ServiceType serviceType) {
        Map<String, Service> entries = new HashMap<>();
        try (io.valkey.Jedis jedis = jedisPool.getResource()) {
            String serviceKey = ReservedKeys.getServiceKey(serviceType);
            Set<String> services = jedis.smembers(serviceKey);

            for (String key : services) {
                Service service = newService(jedis, key);

                if (service != null) {
                    entries.put(key, service);
                }
            }

        } catch (Exception e) {
            LOG.error("Failed list services: {}", e.getMessage(), e);
        }
        return entries;
    }

    public void update(String target, String option, String value) {
        try (io.valkey.Jedis jedis = jedisPool.getResource()) {
            String jsonConfiguration = jedis.hget(target, option);
            Configuration configuration = Configuration.fromJson(jsonConfiguration);
            configuration.setValue(value);

            jedis.hset(target, option, configuration.toJson());

            LOG.info("Option {} updated for Service {}", option, target);
        } catch (Exception e) {
            LOG.error("Failed to update option {} for service {}: {}", option, target, e.getMessage(), e);
        }
    }


    /**
     * Creates a new Service object from the given hashmap key.
     *
     * @param jedis The Jedis connection used to retrieve service information.
     * @param key   The hashmap key representing the service.
     * @return A Service object representing the created service.
     */
    private static Service newService(Jedis jedis, String key) {
        Set<String> configs = jedis.hkeys(key);
        return toService(jedis, key, configs);
    }


    /**
     * Creates a new Service object from the given hashmap key and configuration keys.
     *
     * @param jedis The Jedis connection used to retrieve service information.
     * @param key   The hashmap key representing the service.
     * @param configs The set of configuration keys for the service.
     * @return A Service object representing the created service.
     */
    private static Service toService(Jedis jedis, String key, Set<String> configs) {
        String address = jedis.hget(key, ReservedKeys.WANAKU_TARGET_ADDRESS);
        if (address == null) {
            return null;
        }

        Service service = new Service();

        service.setTarget(address);

        Map<String, Configuration> configurationMap = new HashMap<>();

        for (String config : configs) {
            if (!ReservedKeys.ALL_KEYS.contains(config)) {
                Configuration configuration = toConfiguration(jedis, key, config);
                configurationMap.put(config, configuration);
            }
        }

        Configurations configurations = new Configurations();
        configurations.setConfigurations(configurationMap);
        service.setConfigurations(configurations);

        return service;
    }


    /**
     * Creates a new Configuration object from the given hashmap key and configuration value.
     *
     * @param jedis The Jedis connection used to retrieve configuration information.
     * @param config The hashmap key representing the configuration.
     * @return A Configuration object representing the created configuration.
     */
    private static Configuration toConfiguration(Jedis jedis, String key, String config) {
        String jsonConfiguration = jedis.hget(key, config);

        return Configuration.fromJson(jsonConfiguration);
    }
}
