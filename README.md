# WAU Captcha SDK for Java

`com.waucaptcha:wau-captcha-sdk` — redeem WAU Captcha tokens on your server, and fetch or answer challenges from
Java frontends. Java 17 or later, no dependencies.

## Install

Maven:

```xml
<dependency>
    <groupId>com.waucaptcha</groupId>
    <artifactId>wau-captcha-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

Gradle:

```kotlin
implementation("com.waucaptcha:wau-captcha-sdk:1.0.0")
```

## Verify a token on your server

Your frontend (for example with `@wau-captcha/client`) obtains a one-time token once the user solves a challenge
and sends it with the form. Redeem it with the **secret key** (`sk_…`):

```java
import com.waucaptcha.SiteVerifyResult;
import com.waucaptcha.WauCaptchaClient;
import com.waucaptcha.WauCaptchaException;

WauCaptchaClient wau = WauCaptchaClient.builder()
        .baseUrl("https://captcha.example.com")
        .secretKey(System.getenv("WAU_SECRET_KEY"))
        .build();

try {
    SiteVerifyResult result = wau.verifyToken(request.getParameter("wau-captcha-token"));
    if (!result.success()) {
        // unknown, expired, already used, or issued for another key
    }
} catch (WauCaptchaException e) {
    // the token could not be checked (e.getCode(): SECRET_KEY_INVALID, NETWORK_ERROR, …): fail closed
}
```

Build the client once and share it: it is immutable and thread-safe.

### Spring Boot

```java
@Bean
WauCaptchaClient wauCaptcha(@Value("${wau.base-url}") String baseUrl, @Value("${wau.secret-key}") String secretKey) {
    return WauCaptchaClient.builder().baseUrl(baseUrl).secretKey(secretKey).build();
}

@PostMapping("/signup")
ResponseEntity<String> signup(@RequestParam("wau-captcha-token") String token) {
    if (!wauCaptcha.verifyToken(token).success()) {
        return ResponseEntity.badRequest().body("Please solve the captcha.");
    }
    // … create the account
    return ResponseEntity.ok("Welcome!");
}
```

## Challenges from a Java frontend

With the public **site key** (`pk_…`):

```java
WauCaptchaClient captcha = WauCaptchaClient.builder()
        .baseUrl("https://captcha.example.com")
        .siteKey("pk_…")
        .build();

Challenge challenge = captcha.newChallenge(ChallengeType.IMAGE_DISTORT);
byte[] png = challenge.image();          // or challenge.dataUrl() for an HTML <img>
AnswerResult answer = captcha.verifyAnswer(challenge.id(), userInput);
if (answer.success()) {
    sendToBackend(answer.token());
} else {
    // a challenge can be answered once: fetch a new one
}
```

## Errors

Calls throw the unchecked `WauCaptchaException`, whose `getCode()` is the reason the service gave
(`SECRET_KEY_INVALID`, `SITE_KEY_INVALID`, `ORIGIN_NOT_ALLOWED`, `API_KEY_SUSPENDED`, `ACCOUNT_BANNED`,
`DAILY_QUOTA_EXCEEDED`, `CHALLENGE_NOT_FOUND`, `RATE_LIMITED`, …) or `NETWORK_ERROR`, `TIMEOUT`, `INTERRUPTED`,
`INVALID_RESPONSE` when no usable reply arrived.

## Build

```bash
./mvnw package          # target/wau-captcha-sdk-1.0.0.jar
```

## Publish to Maven Central

1. Create an account on [central.sonatype.com](https://central.sonatype.com) and verify the namespace
   `com.waucaptcha` (it is proven through a DNS record of the domain `waucaptcha.com`). Without that domain, use a
   namespace you can verify, such as `io.github.<user>`, and change `groupId` accordingly.
2. Generate a user token there and put it in `~/.m2/settings.xml`:

   ```xml
   <settings>
     <servers>
       <server>
         <id>central</id>
         <username>TOKEN_USERNAME</username>
         <password>TOKEN_PASSWORD</password>
       </server>
     </servers>
   </settings>
   ```

3. Create a GPG key and publish it to a key server: `gpg --gen-key`, then
   `gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>`.
4. Set `project.homepage` and `project.repository` in `pom.xml` to the real project page and Git repository.
5. Release:

   ```bash
   ./mvnw -P release deploy
   ```

   The deployment then shows up on central.sonatype.com, where you publish it.

## License

MIT
