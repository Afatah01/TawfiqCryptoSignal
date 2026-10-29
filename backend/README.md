# Backend (Render)
Deploy: render.com > New + > Blueprint > connect this GitHub repo > Apply (render.yaml at repo root).
Or manually: New Web Service, Root Directory=backend, Build=`pip install -r requirements.txt`, Start=`uvicorn app:app --host 0.0.0.0 --port $PORT`.
Then paste your https://<service>.onrender.com URL into the Android app: Settings > Render backend URL (builds default to https://tawfiq-cryptosignal.onrender.com).
Free Render services sleep when idle; first request may take ~30-60s. No env secrets needed.
