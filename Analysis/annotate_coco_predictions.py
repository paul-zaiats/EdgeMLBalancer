# Create a ready-to-run Python script that overlays COCO predictions (and optionally ground-truth) on images.
# It reads:
#   --images_dir: path to COCO images directory (e.g., val2017)
#   --annotations_json: path to instances_val2017.json (for mapping image_id -> file_name and category_id -> name)
#   --predictions_json: path to predictions.json in COCO results format
#   --out_dir: where to write annotated images
#   --score_thresh: optional float, default 0.0
#   --draw_gt: optional flag to also overlay ground-truth boxes for comparison
#   --limit: optional int to annotate only the first N images (useful for quick checks)
#
# Output: annotated images saved to out_dir; prints a small summary.
import argparse
import json
import os
from pathlib import Path
from collections import defaultdict

from PIL import Image, ImageDraw, ImageFont

# Try to get a font; fallback to default if unavailable
def get_font(size=16):
    try:
        # macOS/Linux common path; will fallback otherwise
        return ImageFont.truetype("DejaVuSans.ttf", size)
    except Exception:
        try:
            return ImageFont.load_default()
        except Exception:
            return None

def load_coco_annotations(annotations_json):
    with open(annotations_json, "r") as f:
        anns = json.load(f)

    # image_id -> file_name
    image_id_to_file = {img["id"]: img["file_name"] for img in anns.get("images", [])}

    # category_id -> name
    cat_id_to_name = {c["id"]: c["name"] for c in anns.get("categories", [])}

    # ground truth annotations grouped by image_id (optional)
    gt_by_image = defaultdict(list)
    for a in anns.get("annotations", []):
        if a.get("iscrowd", 0) == 1:
            continue
        image_id = a["image_id"]
        bbox = a["bbox"]  # [x,y,w,h] in pixels
        cat_id = a["category_id"]
        gt_by_image[image_id].append((bbox, cat_id))

    return image_id_to_file, cat_id_to_name, gt_by_image

def load_predictions(predictions_json, score_thresh=0.0):
    with open(predictions_json, "r") as f:
        preds = json.load(f)
    # group by image_id
    by_image = defaultdict(list)
    for p in preds:
        score = float(p.get("score", 0.0))
        if score < score_thresh:
            continue
        iid = int(p["image_id"])
        cid = int(p["category_id"])
        bbox = p["bbox"]  # [x,y,w,h] in pixels
        by_image[iid].append((bbox, cid, score))
    return by_image

def draw_box(draw, bbox, color, width=3):
    x, y, w, h = bbox
    x2, y2 = x + w, y + h
    # Ensure float -> int for drawing
    draw.rectangle([(x, y), (x2, y2)], outline=color, width=width)

def draw_label(draw, xy, text, fill=(255,255,255), bg=(0,0,0,160), font=None, padding=2):
    if font is None:
        font = get_font(16)
    text_w, text_h = draw.textlength(text, font=font), font.size if hasattr(font, 'size') else 16
    x, y = xy
    rect = [x, y - text_h - 2*padding, x + text_w + 2*padding, y]
    draw.rectangle(rect, fill=bg)
    draw.text((x + padding, y - text_h - padding), text, fill=fill, font=font)

def annotate_image(img_path, preds, cats, gts=None, out_path=None):
    im = Image.open(img_path).convert("RGB")
    draw = ImageDraw.Draw(im, "RGBA")

    # Draw predictions in RED
    for bbox, cat_id, score in preds:
        draw_box(draw, bbox, color=(255, 0, 0, 255), width=3)
        name = cats.get(cat_id, str(cat_id))
        label = f"{name} {score:.2f}"
        x, y, w, h = bbox
        draw_label(draw, (x, y), label, font=get_font(16))

    # Optionally draw ground truth in BLUE
    if gts is not None:
        for bbox, cat_id in gts:
            draw_box(draw, bbox, color=(66, 133, 244, 255), width=2)  # Google blue
            name = cats.get(cat_id, str(cat_id))
            x, y, w, h = bbox
            draw_label(draw, (x, y), f"GT: {name}", fill=(255,255,255), bg=(66,133,244,160), font=get_font(14))

    if out_path:
        im.save(out_path, quality=95)

def main():
    ap = argparse.ArgumentParser(description="Overlay COCO predictions (and optionally ground-truth) on images.")
    ap.add_argument("--images_dir", required=True, help="Path to COCO images dir (e.g., val2017)")
    ap.add_argument("--annotations_json", required=True, help="Path to instances_val2017.json")
    ap.add_argument("--predictions_json", required=True, help="Path to predictions.json (COCO results format)")
    ap.add_argument("--out_dir", required=True, help="Output directory for annotated images")
    ap.add_argument("--score_thresh", type=float, default=0.0, help="Filter predictions by score")
    ap.add_argument("--draw_gt", action="store_true", help="Also draw ground-truth boxes (blue)")
    ap.add_argument("--limit", type=int, default=None, help="Annotate only first N images that have predictions")
    args = ap.parse_args()

    images_dir = Path(args.images_dir)
    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    img_id_to_file, cat_id_to_name, gt_by_image = load_coco_annotations(args.annotations_json)
    preds_by_image = load_predictions(args.predictions_json, score_thresh=args.score_thresh)

    # If limit is set, reduce the set of image_ids to annotate
    image_ids = list(preds_by_image.keys())
    image_ids.sort()
    if args.limit is not None:
        image_ids = image_ids[:args.limit]

    count_done = 0
    count_missing = 0
    for iid in image_ids:
        fname = img_id_to_file.get(iid)
        if not fname:
            count_missing += 1
            continue
        img_path = images_dir / fname
        if not img_path.exists():
            count_missing += 1
            continue

        preds = preds_by_image[iid]
        gts = gt_by_image[iid] if args.draw_gt else None

        out_path = out_dir / fname
        out_path.parent.mkdir(parents=True, exist_ok=True)
        annotate_image(img_path, preds, cat_id_to_name, gts=gts, out_path=out_path)
        count_done += 1

    print(f"Annotated images written: {count_done}")
    if count_missing:
        print(f"Images missing (no file or no mapping): {count_missing}")

if __name__ == "__main__":
    main()

