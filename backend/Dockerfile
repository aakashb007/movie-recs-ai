FROM python:3.12-slim

WORKDIR /srv
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY recsys recsys
COPY app app
COPY reports reports
# MovieLens may not be redistributed, so it is downloaded and the model trained at build time.
RUN python -m recsys.train --dataset ml-latest-small --out artifacts/model.pkl && rm -rf data

ENV MODEL_PATH=/srv/artifacts/model.pkl PORT=8000
EXPOSE 8000
CMD ["sh", "-c", "uvicorn app.main:app --host 0.0.0.0 --port ${PORT}"]
