# WAU Captcha SDK for Java

`com.waucaptcha:wau-captcha-sdk` — redeem WAU Captcha tokens on your server, as `@wau-captcha/server` does, and
fetch and answer challenges, as `@wau-captcha/client` does without its widget. Java 17 or later, no dependencies.

## Install

Maven:

```xml
<dependency>
    <groupId>com.waucaptcha</groupId>
    <artifactId>wau-captcha-sdk</artifactId>
    <version>1.1.0</version>
</dependency>
```

Gradle:

```kotlin
implementation("com.waucaptcha:wau-captcha-sdk:1.1.0")
```

## Verify a token on your server

On a website, the JavaScript widget (`@wau-captcha/client` or `@wau-captcha/react`) shows the challenge and puts a
one-time token in the form field `wau-captcha-token`. Redeem it with the **secret key** (`sk_…`):

```java
import com.waucaptcha.BehaviorVerdict;
import com.waucaptcha.SiteVerifyResult;
import com.waucaptcha.WauCaptchaClient;
import com.waucaptcha.WauCaptchaException;

WauCaptchaClient wau = WauCaptchaClient.builder()
        .baseUrl("https://api.waucaptcha.com")
        .secretKey(System.getenv("WAU_SECRET_KEY"))
        .build();

try {
    SiteVerifyResult result = wau.verifyToken(request.getParameter("wau-captcha-token"));
    // hostname is the website the captcha was solved on: it must be yours.
    if (!result.success() || !result.hostname().equals("shop.example.com")) {
        // unknown, expired, already used, issued for another key, or solved on another website
    }
    // Sign-up accepts human only: suspicious is a scripted session, and not_measured means the
    // client sent no behaviour data, so it is untrusted.
    if (result.behavior().verdict() != BehaviorVerdict.HUMAN) {
        // ask again
    }
} catch (WauCaptchaException e) {
    // the token could not be checked (e.getCode(): SECRET_KEY_INVALID, NETWORK_ERROR, …): fail closed
}
```

A successful result carries `challengeType()` (`"animal_jump"`; `ChallengeType.fromWireName` turns it into the
enum), `solvedAt()`, `behavior()` (`verdict()`, `score()`, `reasons()`) and `hostname()`. The token travels in the
JSON body of `POST /siteverify` with `Authorization: Bearer <secret key>`, never in the URL. The base URL must be
https; plain http is accepted for `localhost`, `127.0.0.1` and `[::1]` only.

Build the client once and share it: it is immutable and thread-safe.

### Spring Boot

```java
@Configuration
class CaptchaConfig {
    @Bean
    WauCaptchaClient wauCaptcha(@Value("${wau.base-url}") String baseUrl, @Value("${wau.secret-key}") String secretKey) {
        return WauCaptchaClient.builder().baseUrl(baseUrl).secretKey(secretKey).build();
    }
}

@RestController
class SignupController {
    private final WauCaptchaClient wauCaptcha;

    SignupController(WauCaptchaClient wauCaptcha) {
        this.wauCaptcha = wauCaptcha;
    }

    @PostMapping("/signup")
    ResponseEntity<String> signup(@RequestParam("wau-captcha-token") String token) {
        SiteVerifyResult result;
        try {
            result = wauCaptcha.verifyToken(token);
        } catch (WauCaptchaException e) {
            return ResponseEntity.status(503).body("Captcha check unavailable."); // fail closed
        }
        if (!result.success() || !"shop.example.com".equals(result.hostname())
                || result.behavior().verdict() != BehaviorVerdict.HUMAN) {
            return ResponseEntity.badRequest().body("Please solve the captcha.");
        }
        // … create the account
        return ResponseEntity.ok("Welcome!");
    }
}
```

with `wau.base-url=https://api.waucaptcha.com` and `wau.secret-key=${WAU_SECRET_KEY}` in `application.properties`.

## Challenges from Java

Keys that require behaviour data, the default for new keys, refuse challenge answers sent without a `Behavior`
(`ErrorCode.BEHAVIOR_REQUIRED`), and a Java client records none by itself. So the challenge calls suit tests and
desktop apps that record the behaviour themselves; websites use the JavaScript widget and verify the token in Java.
A Java client also sends no `Origin`: a key bound to domains refuses its challenge calls (`DOMAIN_NOT_ALLOWED`), and
the tokens it earns have an empty `hostname`.

With the public **site key** (`pk_…`):

```java
WauCaptchaClient captcha = WauCaptchaClient.builder()
        .baseUrl("https://api.waucaptcha.com")
        .siteKey("pk_…")
        .build();

// image_distort (a PNG) and motion_noise (an animated GIF): the text in the image.
Challenge text = captcha.newChallenge(ChallengeType.IMAGE_DISTORT);
byte[] png = text.image();                    // or text.dataUrl() for an HTML <img>
AnswerResult typed = captcha.verifyAnswer(text, userInput, behavior);

// slide_puzzle: drag slide.piece() (a PNG, y, width, height) over slide.image() and send the drag.
Challenge slide = captcha.newChallenge(ChallengeType.SLIDE_PUZZLE);
AnswerResult dragged = captcha.verifySlide(slide, new SlideDrag(x, trail), behavior);

// animal_jump: play jump.image() (round 1), then jump.secondImage() (round 2), each once, and click
// the animal that jumps highest in each.
Challenge jump = captcha.newChallenge(ChallengeType.ANIMAL_JUMP);
AnswerResult clicked = captcha.verifyJump(jump, List.of(round1, round2), behavior);

// object_rotate: show frame 0 of turn.image() (round 1), then of turn.secondImage() (round 2); each strip is
// turn.frameCount() frames of width × height side by side. Rotate right shows the next frame, Rotate left the one
// before; Done ends the round on the frame on screen.
Challenge turn = captcha.newChallenge(ChallengeType.OBJECT_ROTATE);
AnswerResult turned = captcha.verifyRotate(turn, List.of(
        new RotateRound(3, List.of(new RotatePress(812, 1), new RotatePress(1240, 1), new RotatePress(1531, 1)), 2100),
        new RotateRound(6, List.of(new RotatePress(600, -1), new RotatePress(900, -1)), 1300)), behavior);

if (clicked.success()) {
    sendToBackend(clicked.token()); // the backend redeems it with verifyToken
} else {
    // a challenge can be answered once: fetch a new one
}
```

- Every verify call sends the site key, so an answer only counts for the key whose challenge it solves, and solves
  the proof of work the challenge asks for first (`powSalt()`, `powDifficulty()`: the decimal nonce for which
  SHA-256(salt ‖ id ‖ nonce) starts with that many zero bits, as the browser SDK finds it; milliseconds at the
  default 15 bits). Answers are not case-sensitive.
- `Behavior` has the fields the browser SDK records: times in ms since the challenge was shown, pointer samples in
  CSS pixels from the top-left corner of your challenge view, the intervals between key presses (never the keys),
  and `Behavior.VERSION`. The service checks them like the browser's, so record them from real input.
- `SlideDrag` is the final left edge of the piece and the points of the drag, each `(ms since pointerdown, left edge,
  vertical offset)`, in CSS pixels of the image. `JumpClick` is where the click landed in CSS pixels of the
  300 × 150 image, the ms since the playback of that round started (a replay restarts the count) and the replays
  used in the round, up to 3. A click less than 150 ms after the apex of the high jump, or an answer sent sooner
  after the challenge was issued than both high jumps can be watched, fails the challenge. A `RotateRound` is the
  frame on screen when Done was pressed (from 0), every `RotatePress` (whole ms since the round was shown, and 1 for
  Rotate right, the object turning clockwise as seen from above, or -1 for Rotate left) and when Done was pressed.
  The service takes 1 to 24 presses a round, the first at least 250 ms in and the rest at least 60 ms apart, which
  must lead to the frame sent; both rounds must end facing the arrow. The answers go out as the same JSON as the
  browser SDK's.
- `newChallenge()` asks for the type set with `Builder.challengeType`, `IMAGE_DISTORT` by default. SLIDE_PUZZLE and
  ANIMAL_JUMP need a pointer: where keyboard-only users must get through, keep IMAGE_DISTORT available. OBJECT_ROTATE
  works with a pointer or the keyboard, but needs sight.

## Errors

Calls throw the unchecked `WauCaptchaException`. `getCode()` is a string, so reasons newer than this SDK still come
through; compare it with the `ErrorCode` constants:

- from the service: `SECRET_KEY_INVALID`, `SITE_KEY_INVALID`, `ORIGIN_NOT_ALLOWED`, `DOMAIN_NOT_ALLOWED`,
  `CHALLENGE_TYPE_UNSUPPORTED`, `CHALLENGE_TYPE_NOT_ALLOWED`, `CHALLENGE_NOT_FOUND`, `CHALLENGE_ID_INVALID`,
  `BEHAVIOR_REQUIRED`, `API_KEY_SUSPENDED`, `ACCOUNT_BANNED`, `DAILY_QUOTA_EXCEEDED`, `RATE_LIMITED`,
  `PAYLOAD_TOO_LARGE`, `INTERNAL`, `ROUTE_NOT_FOUND`, `UPSTREAM_UNAVAILABLE`;
- raised by the SDK when no usable reply arrived: `NETWORK_ERROR`, `TIMEOUT`, `INTERRUPTED`, `INVALID_RESPONSE`, and
  `UNKNOWN` for an error reply that named no reason.

`getStatus()` is the HTTP status (0 without a reply), `getMetadata()` the details the service attached, and
`getRetryAfter()` the seconds a `RATE_LIMITED` reply asks to wait.

```java
} catch (WauCaptchaException e) {
    switch (e.getCode()) {
        case ErrorCode.RATE_LIMITED -> retryIn(e.getRetryAfter().orElse(60));
        case ErrorCode.CHALLENGE_NOT_FOUND -> showNewChallenge();
        default -> log.warn("captcha check failed: {}", e.getCode());
    }
}
```

## Build

```bash
./mvnw verify    # compiles, runs the tests, target/wau-captcha-sdk-1.1.0.jar
```

JDK 17 or later; CI runs it on Java 17 and 21. `./mvnw -P release -Dgpg.skip package` also builds the sources and
javadoc jars that Maven Central requires, without signing them.

## Publish to Maven Central

1. Create an account on [central.sonatype.com](https://central.sonatype.com) and add the namespace `com.waucaptcha`.
   The portal proves it through a DNS TXT record on the domain `waucaptcha.com`: add the record it shows, then
   verify the namespace.
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
4. Release:

   ```bash
   ./mvnw -P release deploy
   ```

   It builds and signs the jar, the sources and the javadoc and uploads them to the Central Portal; the deployment
   then shows up on central.sonatype.com, where you publish it.

## License

MIT
