/**
 * Sign-in and roles. The dashboard signs in with a form post and keeps a
 * session in Redis through Spring Session. Scripts and the CLI use HTTP
 * Basic. VIEWER may read, ADMIN may also change.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Security")
package io.github.jkanie.schedkeel.security;
