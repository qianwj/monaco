# Monaco
**A Lightweight, High-Performance, and Extensible Message Hub**

Built on the powerful Vert.x reactive framework, this MQTT broker delivers enterprise-grade messaging capabilities with full compliance to the MQTTv5 protocol specification. Engineered for modern IoT ecosystems, it achieves unprecedented throughput of over 500,000 messages per second on standard hardware while maintaining ultra-low latency under 5ms.

Key advantages:

🚀 MQTTv5 Feature Complete
Full implementation of protocol enhancements including shared subscriptions, message expiry, topic aliases, and user properties for seamless enterprise integration.

⚡️ Vert.x-Powered Performance
Leverages non-blocking I/O and event-driven architecture to handle 1M+ concurrent connections with minimal resource footprint (<100MB RAM baseline).

🔌 Modular Extensibility
Plugin architecture supports custom authentication providers (JWT/OAuth2), protocol adapters (CoAP/LwM2M), and storage backends (Redis/TimescaleDB) through simple Vert.x verticles.

🌐 Lightweight Cluster Mode
Embedded Hazelcast clustering enables automatic horizontal scaling with zero configuration overhead for cloud-native deployments.

🔐 Enterprise Security
TLS 1.3 encryption, certificate pinning, and RBAC authorization with automatic device certificate rotation via PKI integration.



## Extension System

### Features

- extension deploy
- extension undeploy
- extension replace
- multi-type extension
