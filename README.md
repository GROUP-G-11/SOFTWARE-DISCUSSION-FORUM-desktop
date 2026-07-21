# Smart Discussion Forum — Desktop Client

Java Swing desktop client for the Smart Discussion Forum, built to talk to
the same Laravel backend as the web client. Built with Maven; targets Java 17+.

## Setup in IntelliJ (2025.x)

1. `File > Open...` and select this folder (the one containing `pom.xml`).
   IntelliJ will detect it as a Maven project and prompt to load it —
   accept, and it will download dependencies from Maven Central
   automatically (needs normal internet access; this sandbox's network
   couldn't reach Maven Central, so dependency resolution was verified a
   different way here — see "How this was verified" below).
2. Set the Project SDK to Java 17 or newer (`File > Project Structure >
   Project`).
3. Run `com.smartforum.desktop.Main` (right-click it → Run), or use the
   Maven `Run` configuration IntelliJ generates for the `exec` goal.

## Running from the command line

```bash
mvn clean package
java -jar target/smart-discussion-forum-desktop.jar
```

The shaded jar bundles every dependency, so that single `java -jar` command
is all that's needed once it's built.

## Pointing it at your Laravel backend

By default it targets `http://127.0.0.1:8000/api` (a local `php artisan
serve`). To point at something else, either:

- Drop a `config.properties` on the classpath (e.g. in
  `src/main/resources/`) with:
  ```
  api.baseUrl=http://your-host:port/api
  ```
- Or pass a JVM system property at launch:
  ```bash
  java -Dsdf.api.baseUrl=http://your-host:port/api -jar target/smart-discussion-forum-desktop.jar
  ```

## Architecture note

The whole app is a single window (`AppWindow`), with login, registration,
and the signed-in dashboard as cards in one `CardLayout` — clicking
"Register here", "Log in", or logging out swaps the card in place rather
than opening/closing separate windows. This mirrors the single-page-app
shape of the Laravel web client (and of the previous desktop client this
one replaces). `DashboardChrome` itself is a panel for the same reason, not
a window.

Login and registration are laid out and sized the same way as the previous
desktop client (centered card, generous field spacing, large title/label
fonts) but recolored to the Laravel palette: a dark `Theme.INK` backdrop,
a cream `Theme.PAPER` card, and `Theme.ACCENT` green for the primary
button. Registration has no role picker — every new account starts as a
Student (matching the web client and the actual `/register` endpoint,
which defaults role server-side); becoming a Lecturer is an Administrator
action via Role Management, not a self-service choice at sign-up.

## Changelog — role accuracy pass

Checked every admin/lecturer panel directly against the real Laravel blade
files and controllers (not memory/assumption), and fixed several real
mismatches:

- **Admin no longer has any access to group messages.** Confirmed against
  `admin.blade.php`'s own comment ("Group name is plain text — Statistics
  and Gradebook stay as admin-facing actions"). `GroupsPanel` now takes a
  `Mode` (STUDENT/LECTURER/ADMIN); ADMIN mode shows only Statistics/
  Gradebook buttons per group, no click-through, no join button.
- **Lecturer's "Gradebook" nav item removed.** It never existed as a
  standalone nav item in the real app - only "Scoring Criteria" does
  (`#panel-criteria`). Added `ScoringCriteriaPanel` matching that real
  content. Gradebook is now reachable the same way it is in Laravel: a
  button on a group's card, for whoever owns/administers that group.
- **Admin's Warnings/Blacklists split into two real panels** ("Inactivity
  Warnings", "Blacklisted Users") instead of an invented combined
  "Content Moderation" tab view. Also removed a "scan for inactivity"
  button that doesn't exist anywhere in the real UI (it's a
  scheduled/console-only operation server-side).
- **Lecturer's quiz creation UI rewritten** to match
  `dashboard/lecturer.blade.php` structure: an inline form (target group,
  title, date/time/duration, a dynamic question matrix) stacked above
  "Your quizzes", instead of a popup dialog.
- **Fixed a real, meaningful bug found during this pass**: the previous
  quiz-creation flow called `publish` immediately after `create`, which
  (per `QuizController::publish()`) unconditionally forces status to
  `Open` - meaning every new quiz was immediately attemptable regardless
  of its scheduled time. Removed; quizzes are now created as `Scheduled`
  only, matching the real submit handler, and open themselves at the
  configured time (or via the existing manual Publish button).
- **Fixed wrong field names** in the gradebook/my-grade data mapping
  (checked directly against `GradingController`'s actual JSON shape).
- **ProfilePanel rebuilt** with a centered card, an avatar-initials circle,
  and consistent field spacing.
- Investigated a reported "can't type in the message composer" issue with
  an actual GUI automation test (Xvfb + `Robot` synthetic keystrokes)
  rather than guessing - the composer works correctly once a topic is
  actually open; no bug found there.

## What's implemented

Every panel talks to the real endpoints in the Laravel project's
`routes/api.php` — field names were taken directly from the actual
controllers, not guessed.

- **Auth (5.1)**: register (with rules acceptance), login, logout, offline
  login (via a locally-stored salted hash, written after the first
  successful online login).
- **Groups**: list/join/create.
- **Topics & Posts (5.3 + part of 5.4)**: topic list, topic thread
  (posts/replies), member-exclusion checklist, PDF export, social-media
  forward.
- **Offline support (5.4)**: local SQLite cache of groups/topics/posts/
  quizzes/notifications; an outbox queue for posts/replies composed while
  offline; a `SyncService` that replays the outbox and pulls the delta via
  `POST /sync` on a 20-second background poll.
- **Quiz Engine (5.5)**: lecturer quiz configuration (dynamic question
  matrix, publish/close), student quiz attempt with a real countdown timer,
  focus-locked modal, and auto-submit on timeout.
- **Grading (5.6)**: student "My Grades" and lecturer Gradebook (both with
  their own group picker, since the API is per-group), scoring-criteria
  creation.
- **Statistics (5.7)**: Administrator system-wide overview.
- **Recommendations (5.8)**: "Recommended for you" topic list.
- **Social sharing (5.9)**: forward-to-platform on each post.
- **Notifications (5.10)**: list, mark read / mark all read.
- **Moderation (5.2)**: warnings list + resolve, active blacklists + lift,
  on-demand inactivity scan.
- **Role management**: Administrator user search + role assignment.
- **Profile**: self-service edit (name/bio/phone/department).

## Known gaps / next steps

- **Profile picture upload** isn't wired up yet (the endpoint exists
  server-side; the multipart upload UI doesn't yet).
- **Real-time push** (Reverb/WebSocket) isn't wired into the UI yet — the
  `Java-WebSocket` dependency is in `pom.xml` for this, but panels
  currently rely on the 20-second sync poll rather than an open socket.
  Posts/replies/notifications still show up, just not instantly.
- The **recommended-topic click-through** currently sends the person to
  Groups rather than jumping straight to the topic, since the
  recommendations endpoint doesn't include the topic's parent group id.
- No automated test suite yet (JUnit is wired into `pom.xml` but no tests
  were written this pass).

## How this was verified

This sandbox's network allowlist doesn't include Maven Central, so `mvn
compile` itself couldn't be used directly to verify the code here. Instead:
every dependency's actual jar (or, for `org.json`, compiled source) was
fetched from its GitHub releases/repository, and the full source tree was
compiled against those with `javac` directly — a real compile, just not
through Maven. That compile is clean (only standard, harmless Swing
warnings like missing `serialVersionUID`). The app was also smoke-tested
under Xvfb (a virtual display) to confirm it actually launches to the login
screen with no runtime exceptions, and that the local SQLite cache file is
created correctly. `mvn compile`/`mvn package` themselves should work
normally on any machine with regular internet access, since `pom.xml`'s
dependency versions were confirmed to be real, current releases.
