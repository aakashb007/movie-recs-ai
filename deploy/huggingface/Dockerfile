# Hugging Face Spaces (free, no card): create a Docker Space, add this one file, and set
# TMDB_API_KEY and GROQ_API_KEY under Settings -> Variables and secrets (as *secrets*).
# It pulls the backend from GitHub, downloads MovieLens and trains the model while building.
FROM python:3.12-slim

RUN apt-get update && apt-get install -y --no-install-recommends git && rm -rf /var/lib/apt/lists/*
RUN useradd -m -u 1000 user
WORKDIR /srv

ARG REPO=https://github.com/aakashb007/movie-recs-ai
RUN git clone --depth 1 "$REPO" /tmp/repo && cp -r /tmp/repo/backend/. /srv && rm -rf /tmp/repo
RUN pip install --no-cache-dir -r requirements.txt
RUN python -m recsys.train --dataset ml-latest-small --out artifacts/model.pkl && rm -rf data && chown -R user /srv

USER user
# Spaces route traffic to port 7860.
ENV MODEL_PATH=/srv/artifacts/model.pkl PORT=7860
EXPOSE 7860
CMD ["sh", "-c", "uvicorn app.main:app --host 0.0.0.0 --port ${PORT}"]
