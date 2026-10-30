# Reelwise: movie and TV recommendations you can check

> **This branch (`on-device`) runs entirely on the phone. No server.** You paste your own
> free TMDB key (and optionally a Groq key) on first launch; the recommendation engine in
> `android/app/src/main/java/com/reelwise/app/engine/` is a Kotlin port of `backend/app/`
> without the MovieLens model. Download the latest build:
> [reelwise-on-device.apk](https://github.com/aakashb007/movie-recs-ai/raw/apk-on-device/reelwise-on-device.apk).

An Android app (Kotlin, Jetpack Compose, Material 3) backed by a small Python service that
recommends movies and series, explains every pick, and lets you ask for something in plain
words ("something like Dark but lighter").

The "AI" here is specific and testable, not a chatbot guessing titles:

| Part | What it does | How we know it works |
|---|---|---|
| **Collaborative model** | Implicit-feedback ALS + item-kNN trained on MovieLens ratings, folded in from your likes without retraining | Offline evaluation vs. popularity and item-kNN baselines on a temporal split; CI fails if it doesn't win (see `backend/reports/`) |
| **Content model** | TF-IDF/SVD over genres, tags and plot text; carries brand-new users (cold start) | Separate cold-start evaluation on users the model never saw, given 5 ratings |
| **Hybrid + diversity** | Blend whose weights are chosen on a validation split; MMR reranking so a list isn't ten near-identical films | Diversity, coverage and novelty reported next to accuracy |
| **Natural-language Ask** | Groq LLM (free tier, `llama-3.3-70b-versatile`) turns a request into filters. **It never picks titles**: every result comes from TMDB | `test_llm_cannot_inject_titles_that_do_not_exist` feeds the LLM an invented title and checks only real catalog IDs come back |
| **Explanations** | "Fans of *Arrival* also loved this", "Because you liked *Heat* · Crime" | Each reason points to a title you actually rated (tested) |

### Measured results (MovieLens, held-out data, NDCG@10, higher is better)

| | Popularity | Item-kNN | **Reelwise hybrid** |
|---|---|---|---|
| Existing users (latest 20% of history held out, 590 users) | 0.075 | 0.103 | **0.110** |
| New users, 5 ratings (never seen in training, 122 users) | 0.310 | 0.248 | **0.356** |

Every push re-runs this and CI fails if the hybrid stops beating both baselines in either row.
Full tables (recall, coverage, diversity, novelty): [`backend/reports/eval-ml-latest-small.md`](backend/reports/eval-ml-latest-small.md).

The first version lost to popularity for new users (0.283 vs 0.310): five ratings aren't
enough to beat "what most people liked". The fix adds popularity weight that fades as a
person's history grows, tuned on validation users kept separate from the test users.

## Screenshots

Rendered by the screenshot tests on every push to `main` (artwork shows title placeholders
because tests run offline):

| For you | Ask | Details | Taste quiz |
|---|---|---|---|
| ![For you](https://raw.githubusercontent.com/aakashb007/movie-recs-ai/screenshots/home_dark.png) | ![Ask](https://raw.githubusercontent.com/aakashb007/movie-recs-ai/screenshots/ask.png) | ![Details](https://raw.githubusercontent.com/aakashb007/movie-recs-ai/screenshots/detail.png) | ![Quiz](https://raw.githubusercontent.com/aakashb007/movie-recs-ai/screenshots/onboarding.png) |

TV series use TMDB's recommendations plus the content model; MovieLens has no TV data, so
the offline evaluation covers movies only.

## Repository layout

```
backend/   FastAPI service + recsys/ (models, evaluation, training) + tests
android/   The Reelwise app
.github/   CI: backend tests + MovieLens evaluation + Docker smoke test; Android lint, tests, screenshots, APKs
```

## Run the backend

Keys stay on the server. The APK never contains them.

```bash
cd backend
pip install -r requirements-dev.txt
python -m recsys.train                     # downloads MovieLens (small) and trains, ~1 min
export TMDB_API_KEY=...                    # v3 key or v4 read token both work
export GROQ_API_KEY=...                    # optional; without it Ask uses a rule-based parser
uvicorn app.main:app --reload --port 8000
```

Re-run the evaluation yourself with `python -m recsys.evaluate --dataset ml-latest-small`
(`--dataset ml-32m` also works but item-kNN needs a lot of memory at that size).

**Free hosting:** [![Deploy to Render](https://render.com/images/deploy-to-render-button.svg)](https://render.com/deploy?repo=https://github.com/aakashb007/movie-recs-ai)
`render.yaml` deploys the Docker image on Render's free plan. Add the two
keys as environment variables there. Free instances sleep when idle, so the first request
after a while takes ~30 s.

## Run the app

```bash
cd android
./gradlew installDebug        # or assembleRelease: app/build/outputs/apk/release/app-release.apk
```

On first launch the app asks for your **TMDB key** (free at themoviedb.org → Settings → API;
a v3 key or a v4 read access token both work) and checks it with TMDB before saving. The
**Groq key** is optional and only makes Ask smarter. The **OMDb key** is optional too and adds
IMDb ratings (TMDB itself has none). Keys are stored in their own DataStore
file on the phone, which the backup rules leave out, and can be changed in Profile → API keys.

CI builds both APKs on every push and also publishes the release APK to the `apk-<branch>`
branch for a direct download.

### What's in the app
- **Taste quiz (4 steps):** tick everything you've already watched (search covers all of TMDB) and
  heart the ones you loved; love or ban genres; choose how new, movies or series, and languages;
  review and save. Watched titles are never suggested; loved ones seed "because you loved".
- **For you:** every pick as one big poster; swipe left and right. **Browse** has the same picks in rows
  titled by their reason ("Because you loved …"), fresh releases and series.
- **Circle menu (frosted glass):** press and hold any poster, slide onto a petal (the phone ticks on each one) and let go:
  Details, Loved it, My list, Seen it, Not for me, Not interested. The app behind it blurs.
- **Feel-good** and **Classics** chips: uplifting comedy/family/animation/romance (never horror or thrillers),
  or films from before 2000 including cult favourites, still ranked by your taste.
- **Minimum rating** (quiz): IMDb when you add a free OMDb key (Profile → API keys), otherwise TMDB.
- **Ask:** type a mood or a comparison. The app shows how it read your request
  (genres, exclusions, decade, "Like Dark") so a wrong reading is visible.
- **Details:** where to watch in your region (stream/free/rent/buy), trailer, cast, more like this.
- **Watchlist** and **Profile** (region, Material You colours, clear taste profile).
- Works offline from the last cached feed; refreshes in the background on Wi-Fi (WorkManager).
- **Google sign-in (optional, Profile → Google account):** backs up ratings, watchlist and taste quiz
  to a hidden app folder in your own Google Drive (`drive.appdata` scope; the app can't see your other
  files) and restores them on a new phone. API keys are never backed up.

### Setting up Google sign-in
Google only lets an app sign in if it is registered in a Google Cloud project, and it checks the
SHA-1 of the key the APK is signed with. So the APK needs one fixed signing key:
1. Create a keystore (alias `reelwise`), then add two repository secrets: `REELWISE_KEYSTORE_B64`
   (the file, base64-encoded) and `REELWISE_KEYSTORE_PASSWORD`. CI signs both APKs with it.
2. Google Cloud Console → APIs & Services → enable the **Google Drive API**.
3. OAuth consent screen: add the scope `.../auth/drive.appdata`; while in Testing, add yourself as a test user.
4. Credentials → Create OAuth client ID → **Android**: package `com.reelwise.app` and the keystore's SHA-1
   (`keytool -list -v -keystore reelwise.jks`).

## Data and licences
- This product uses the TMDB API but is not endorsed or certified by TMDB.
- Watch-provider data is supplied to TMDB by JustWatch.
- MovieLens data (GroupLens Research) is licensed for non-commercial research use and may not be
  redistributed. It is downloaded at build time, never committed. Commercial use needs GroupLens's permission.

## Limits, stated plainly
- The serving blend for TMDB candidates (TV, recent releases) uses hand-set weights; only the
  MovieLens movie hybrid is validated offline.
- Screenshot tests record images (CI artifact `screenshots`) but don't yet compare against
  committed baselines.
- Not yet done: Macrobenchmark startup timing on a device, an Accessibility Scanner pass on a
  real device, and the five-person usability test (see `docs/usability-test.md`).
