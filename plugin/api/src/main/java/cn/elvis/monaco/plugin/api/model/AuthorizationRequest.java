package cn.elvis.monaco.plugin.api.model;

/** Typed authorization target; invalid combinations cannot be represented. */
public sealed interface AuthorizationRequest {

    record Connect(ConnectView connection) implements AuthorizationRequest {
        public Connect {
            if (connection == null) {
                throw new IllegalArgumentException("Connect authorization view must not be null");
            }
        }
    }

    record Publish(PublishView publication) implements AuthorizationRequest {
        public Publish {
            if (publication == null) {
                throw new IllegalArgumentException("Publish authorization view must not be null");
            }
        }
    }

    record Subscribe(SubscriptionView subscription) implements AuthorizationRequest {
        public Subscribe {
            if (subscription == null) {
                throw new IllegalArgumentException("Subscription authorization view must not be null");
            }
        }
    }
}
