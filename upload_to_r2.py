import boto3
from botocore.config import Config
import os

# R2 credentials
endpoint = "https://e8f8c436890f43c2f52ac3835a3a11ac.r2.cloudflarestorage.com"
akid = "c22f9120483da2d23fd35bb8179cf6fd"
secret = "e3a2f4fc1bd75fea1a3bb4eca8b5eee7c9d617d897a23c223480071d8733ba54"
bucket = "zimrate"

client = boto3.client(
    's3',
    endpoint_url=endpoint,
    aws_access_key_id=akid,
    aws_secret_access_key=secret,
    region_name='auto',
    config=Config(signature_version='s3v4')
)

images_dir = "/opt/forexzim/images"
url_base = "https://pub-b56fdc7dd7bc4e99ab5d1daad8a27630.r2.dev"

for filename in sorted(os.listdir(images_dir)):
    local_path = os.path.join(images_dir, filename)
    if not os.path.isfile(local_path):
        continue

    # Determine content type
    ext = filename.rsplit('.', 1)[-1].lower()
    ct = {'jpg': 'image/jpeg', 'jpeg': 'image/jpeg', 'png': 'image/png', 'webp': 'image/webp'}.get(ext, 'application/octet-stream')

    print(f"Uploading {filename}...", end=" ")
    try:
        client.upload_file(
            local_path, bucket, filename,
            ExtraArgs={'ContentType': ct}
        )
        url = f"{url_base}/{filename}"
        print(f"OK → {url}")
    except Exception as e:
        print(f"ERROR: {e}")