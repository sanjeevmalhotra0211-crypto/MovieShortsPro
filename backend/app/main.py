import os, uuid, shutil, subprocess
from pathlib import Path
from fastapi import FastAPI, UploadFile, File, Form, HTTPException, BackgroundTasks
from fastapi.middleware.cors import CORSMiddleware

BASE = Path(os.getenv("MEDIA_ROOT", "./media")).resolve()
BASE.mkdir(parents=True, exist_ok=True)
app = FastAPI(title="MovieShorts Pro API", version="0.1.0")
app.add_middleware(
    CORSMiddleware, allow_origins=os.getenv("CORS_ORIGINS", "http://localhost").split(","),
    allow_credentials=True, allow_methods=["GET", "POST"], allow_headers=["*"]
)

def run_split(project_dir: Path, source: Path):
    clips = project_dir / "clips"
    clips.mkdir(exist_ok=True)
    # Re-encode for predictable 90-second boundaries. This is CPU intensive.
    cmd = [
        "ffmpeg", "-y", "-i", str(source),
        "-map", "0:v:0", "-map", "0:a?",
        "-c:v", "libx264", "-preset", "veryfast", "-crf", "23",
        "-c:a", "aac", "-b:a", "128k",
        "-force_key_frames", "expr:gte(t,n_forced*90)",
        "-f", "segment", "-segment_time", "90",
        "-reset_timestamps", "1", str(clips / "clip_%04d.mp4")
    ]
    result = subprocess.run(cmd, capture_output=True, text=True)
    (project_dir / "processing.log").write_text(result.stderr[-12000:], encoding="utf-8")
    if result.returncode != 0:
        (project_dir / "status.txt").write_text("failed", encoding="utf-8")
        return
    (project_dir / "status.txt").write_text("ready", encoding="utf-8")

@app.get("/health")
def health():
    return {"status": "ok", "service": "MovieShorts Pro API"}

@app.post("/api/projects")
async def create_project(
    background: BackgroundTasks,
    project_name: str = Form(...),
    movie: UploadFile = File(...)
):
    if not project_name.strip():
        raise HTTPException(400, "Project name is required")
    ext = Path(movie.filename or "").suffix.lower()
    if ext not in {".mp4", ".mov", ".mkv", ".webm", ".m4v"}:
        raise HTTPException(415, "Unsupported video format")
    project_id = str(uuid.uuid4())
    project_dir = BASE / project_id
    project_dir.mkdir(parents=True)
    source = project_dir / ("original" + ext)
    with source.open("wb") as out:
        shutil.copyfileobj(movie.file, out)
    (project_dir / "project_name.txt").write_text(project_name.strip(), encoding="utf-8")
    (project_dir / "status.txt").write_text("processing", encoding="utf-8")
    background.add_task(run_split, project_dir, source)
    return {
        "project_id": project_id,
        "project_name": project_name.strip(),
        "status": "processing",
        "clip_duration_seconds": 90,
        "split_mode": "sequential"
    }

@app.get("/api/projects/{project_id}")
def project_status(project_id: str):
    project_dir = BASE / project_id
    if not project_dir.is_dir():
        raise HTTPException(404, "Project not found")
    clips_dir = project_dir / "clips"
    clips = sorted(p.name for p in clips_dir.glob("*.mp4")) if clips_dir.exists() else []
    status_file = project_dir / "status.txt"
    status = status_file.read_text().strip() if status_file.exists() else "processing"
    name_file = project_dir / "project_name.txt"
    name = name_file.read_text().strip() if name_file.exists() else "Untitled"
    return {"project_id": project_id, "project_name": name, "status": status, "clips": clips}
