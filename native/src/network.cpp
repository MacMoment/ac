/*
 * MacAC Native Library - Networking Implementation
 * 
 * Provides TCP networking for centralized violation reporting.
 * Uses non-blocking I/O with epoll for efficient connection management.
 */

#include "macac_native.h"
#include <cstring>
#include <cstdio>
#include <cerrno>
#include <cinttypes>
#include <unistd.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <fcntl.h>
#include <poll.h>

// Connection state
struct macac_connection {
    int sockfd;
    char host[256];
    int port;
    bool connected;
    char send_buffer[4096];
    size_t send_buffer_len;
};

// ============================================================================
// Internal Helpers
// ============================================================================

/**
 * Set socket to non-blocking mode.
 */
static int set_nonblocking(int sockfd) {
    int flags = fcntl(sockfd, F_GETFL, 0);
    if (flags == -1) {
        return -1;
    }
    return fcntl(sockfd, F_SETFL, flags | O_NONBLOCK);
}

/**
 * Set TCP_NODELAY for low-latency sends.
 */
static int set_tcp_nodelay(int sockfd) {
    int flag = 1;
    return setsockopt(sockfd, IPPROTO_TCP, TCP_NODELAY, &flag, sizeof(flag));
}

/**
 * Resolve hostname to IP address.
 */
static int resolve_host(const char* host, struct sockaddr_in* addr) {
    struct addrinfo hints, *result;
    memset(&hints, 0, sizeof(hints));
    hints.ai_family = AF_INET;
    hints.ai_socktype = SOCK_STREAM;
    
    int ret = getaddrinfo(host, NULL, &hints, &result);
    if (ret != 0) {
        return -1;
    }
    
    // Copy first result
    struct sockaddr_in* ipv4 = (struct sockaddr_in*)result->ai_addr;
    addr->sin_addr = ipv4->sin_addr;
    
    freeaddrinfo(result);
    return 0;
}

/**
 * Format violation data as JSON.
 */
static int format_violation_json(char* buffer, size_t buffer_size,
                                  const char* player_uuid,
                                  const char* category,
                                  double confidence,
                                  double severity,
                                  int64_t timestamp) {
    return snprintf(buffer, buffer_size,
        "{"
        "\"type\":\"violation\","
        "\"player_uuid\":\"%s\","
        "\"category\":\"%s\","
        "\"confidence\":%.6f,"
        "\"severity\":%.6f,"
        "\"timestamp\":%" PRId64
        "}\n",
        player_uuid, category, confidence, severity, timestamp);
}

// ============================================================================
// Public API
// ============================================================================

extern "C" {

macac_connection_t* macac_net_connect(const char* host, int port) {
    if (!host || port <= 0 || port > 65535) {
        return nullptr;
    }
    
    macac_connection_t* conn = new macac_connection_t();
    if (!conn) {
        return nullptr;
    }
    
    memset(conn, 0, sizeof(macac_connection_t));
    strncpy(conn->host, host, sizeof(conn->host) - 1);
    conn->port = port;
    conn->sockfd = -1;
    conn->connected = false;
    
    // Create socket
    conn->sockfd = socket(AF_INET, SOCK_STREAM, 0);
    if (conn->sockfd < 0) {
        delete conn;
        return nullptr;
    }
    
    // Resolve host
    struct sockaddr_in server_addr;
    memset(&server_addr, 0, sizeof(server_addr));
    server_addr.sin_family = AF_INET;
    server_addr.sin_port = htons(port);
    
    if (resolve_host(host, &server_addr) < 0) {
        // Try as IP address
        if (inet_pton(AF_INET, host, &server_addr.sin_addr) <= 0) {
            close(conn->sockfd);
            delete conn;
            return nullptr;
        }
    }
    
    // Set socket options
    set_tcp_nodelay(conn->sockfd);
    
    // Set connection timeout
    struct timeval timeout;
    timeout.tv_sec = 5;
    timeout.tv_usec = 0;
    setsockopt(conn->sockfd, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));
    setsockopt(conn->sockfd, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
    
    // Connect
    if (connect(conn->sockfd, (struct sockaddr*)&server_addr, sizeof(server_addr)) < 0) {
        close(conn->sockfd);
        delete conn;
        return nullptr;
    }
    
    // Set non-blocking after connect
    set_nonblocking(conn->sockfd);
    
    conn->connected = true;
    return conn;
}

int macac_net_send_violation(macac_connection_t* conn,
                              const char* player_uuid,
                              const char* category,
                              double confidence,
                              double severity,
                              int64_t timestamp) {
    if (!conn || !conn->connected || conn->sockfd < 0) {
        return -1;
    }
    
    if (!player_uuid || !category) {
        return -1;
    }
    
    // Format JSON message
    char json_buffer[1024];
    int json_len = format_violation_json(json_buffer, sizeof(json_buffer),
                                          player_uuid, category,
                                          confidence, severity, timestamp);
    
    if (json_len < 0 || json_len >= (int)sizeof(json_buffer)) {
        return -1;
    }
    
    // Send data
    ssize_t sent = send(conn->sockfd, json_buffer, json_len, MSG_NOSIGNAL);
    
    if (sent < 0) {
        if (errno == EAGAIN || errno == EWOULDBLOCK) {
            // Would block, buffer for later
            if (conn->send_buffer_len + json_len < sizeof(conn->send_buffer)) {
                memcpy(conn->send_buffer + conn->send_buffer_len, json_buffer, json_len);
                conn->send_buffer_len += json_len;
                return 0;
            }
            return -1; // Buffer full
        }
        
        // Connection error
        conn->connected = false;
        return -1;
    }
    
    return (int)sent;
}

void macac_net_close(macac_connection_t* conn) {
    if (!conn) {
        return;
    }
    
    if (conn->sockfd >= 0) {
        // Graceful shutdown
        shutdown(conn->sockfd, SHUT_RDWR);
        close(conn->sockfd);
    }
    
    delete conn;
}

int macac_net_is_connected(macac_connection_t* conn) {
    if (!conn || !conn->connected || conn->sockfd < 0) {
        return 0;
    }
    
    // Check if connection is still alive using poll
    struct pollfd pfd;
    pfd.fd = conn->sockfd;
    pfd.events = POLLOUT | POLLERR | POLLHUP;
    
    int ret = poll(&pfd, 1, 0);
    if (ret < 0) {
        conn->connected = false;
        return 0;
    }
    
    if (pfd.revents & (POLLERR | POLLHUP)) {
        conn->connected = false;
        return 0;
    }
    
    return 1;
}

} // extern "C"
