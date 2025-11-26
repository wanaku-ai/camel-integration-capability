# Security Policy

## Reporting Security Vulnerabilities

**Please do not report security vulnerabilities through public GitHub issues.**

Instead, report security vulnerabilities privately to ensure they can be addressed before public disclosure.

### Reporting Process

1. **Email**: Send a detailed report to **contact@wanaku.ai**
2. **Subject**: Use "SECURITY: [Brief Description]" in the subject line
3. **Content**: Include the following information:
   - Description of the vulnerability
   - Steps to reproduce the issue
   - Potential impact and severity
   - Suggested remediation (if any)
   - Your contact information for follow-up

### What to Expect

- **Acknowledgment**: You will receive an acknowledgment within 48 hours
- **Assessment**: We will assess the vulnerability and determine severity within 5 business days
- **Updates**: You will receive regular updates on our progress
- **Resolution**: We aim to release a patch within 30 days for critical issues
- **Credit**: If you wish, we will credit you in the security advisory (unless you prefer to remain anonymous)

### Disclosure Policy

- **Coordinated disclosure**: We follow responsible disclosure practices
- **Embargo period**: Please allow us time to develop and release a fix before public disclosure
- **Public advisory**: We will publish a security advisory once a fix is available

## Supported Versions

Security updates are provided for the following versions:

| Version | Supported          |
| ------- | ------------------ |
| 0.0.9-SNAPSHOT | :white_check_mark: (development) |
| < 0.0.9 | :x: |

> [!IMPORTANT]
> Only the latest release receives security updates. Please upgrade to the latest version to ensure you have all security patches.

## Security Best Practices

### Deployment Security

#### 1. Authentication and Authorization

- **Never use default credentials**: Always set strong, unique client secrets
- **Rotate credentials regularly**: Change OAuth2 client secrets periodically
- **Use separate credentials per environment**: Don't reuse credentials between dev/staging/production
- **Limit token scope**: Request only the OAuth2 scopes needed for operation

#### 2. Secrets Management

- **Never commit secrets**: Don't include secrets in code, configuration files, or route definitions
- **Use environment variables**: Pass secrets via environment variables or mounted files
- **Use secret management systems**
- **Encrypt at rest**: Ensure Kubernetes Secrets are encrypted at rest


#### 3. Route Configuration Security

- **Validate input**: Always validate and sanitize parameters in Camel routes
- **Use parameterized queries**: Never concatenate user input into SQL queries
- **Avoid command injection**: Don't execute shell commands with user-provided input
- **Implement rate limiting**: Protect backend systems from abuse
- **Use route-level access control**: Define exposure rules to restrict sensitive operations

#### 5. Logging and Monitoring

- **Don't log sensitive data**: Never log passwords, tokens, API keys, or PII
- **Monitor for anomalies**: Set up alerts for unusual patterns (high error rates, unexpected access)
- **Audit trail**: Log all tool invocations for security auditing
- **Secure log storage**: Protect logs from unauthorized access

#### 6. Dependency Security

- **Keep dependencies updated**: Regularly update Apache Camel, Wanaku SDK, and other libraries

### Route Development Security

#### Input Validation

Always validate and sanitize user input:

```yaml
- route:
    id: search-products
    from:
      uri: direct:search-products
      steps:
        # Validate search query format
        - validate:
            simple: "${header.query} regex '^[a-zA-Z0-9 ]{1,100}$'"
        # Sanitize before using in backend call
        - setHeader:
            name: SafeQuery
            simple: "${header.query.trim()}"
```

#### Authentication Propagation

When calling authenticated backend APIs:

```yaml
- route:
    id: call-authenticated-api
    from:
      uri: direct:call-api
      steps:
        # Get token from secure store (not from user input!)
        - bean: "tokenProvider"
        - setHeader:
            name: Authorization
            simple: "Bearer ${body}"
        - to: "https://api.example.com/data"
```

#### Error Handling

Don't expose internal details in error messages:

```yaml
- route:
    id: safe-error-handling
    from:
      uri: direct:operation
      steps:
        - doTry:
            - to: "https://backend.example.com/api"
          doCatch:
            - exception:
                - java.lang.Exception
              handled:
                constant: true
              steps:
                # Log detailed error internally
                - log: "ERROR: ${exception.message}"
                # Return generic error to user
                - setBody:
                    constant: "An error occurred processing your request"
```

### Access Control Rules

Implement strict exposure rules:

```yaml
mcp:
  tools:
    definitions:
      # Public tool - low risk
      get-public-info:
        route:
          id: get-public-info

      # Sensitive tool - restricted
      get-salary-info:
        route:
          id: get-salary-info
        # Document that this should only be exposed to authorized users
        # Implement authorization checks within the route
        # Enforce security on Wanaku
```

## Known Security Considerations

### 1. Dynamic Dependency Loading

The capability downloads Maven dependencies at runtime. This introduces risks:

- **Mitigation**: Only load dependencies from trusted Maven repositories
- **Recommendation**: Pre-package common dependencies in the container image
- **Future enhancement**: Implement dependency signature verification

### 2. Route Execution Permissions

Routes execute with the same permissions as the capability process:

- **Mitigation**: Run the capability with minimal necessary permissions
- **Recommendation**: Use Kubernetes security contexts to restrict capabilities
- **Best practice**: Implement least-privilege access for all backend systems

### 3. OAuth2 Token Storage

Access tokens are cached in memory:

- **Current approach**: Tokens are not persisted to disk
- **Risk**: Memory dumps could expose tokens
- **Mitigation**: Ensure memory encryption is enabled in production environments

### 4. gRPC Communication

By default, gRPC communication is not be encrypted:

- **Recommendation**: Use TLS for gRPC in production
- **Future enhancement**: Mutual TLS (mTLS) for service-to-service authentication

## Security Contacts

- **General security inquiries**: contact@wanaku.ai

## Security Resources

- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [Apache Camel Security](https://camel.apache.org/security/)
- [Kubernetes Security Best Practices](https://kubernetes.io/docs/concepts/security/)
- [OAuth 2.0 Security Best Practices](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-security-topics)
