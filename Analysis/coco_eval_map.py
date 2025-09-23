# COCO evaluation helper that computes standard mAP and "mAP@Deadline" (by filtering late predictions).
# It consumes:
#  - --gt: path to instances_val2017.json
#  - --pred: path to predictions.json (COCO "results" format: list of dicts with image_id, category_id, bbox [x,y,w,h], score)
#  - --times_csv: optional CSV with columns image_id,total_ms (per-image total latency)
#  - --deadline_ms: optional deadline; if provided with times_csv, we report mAP@Deadline using only images with total_ms <= deadline
#
# The script prints a small report and saves a JSON with key metrics.

import json
import argparse
from pathlib import Path

def main():
    parser = argparse.ArgumentParser(description="COCO mAP evaluator with optional deadline filtering")
    parser.add_argument("--gt", required=True, help="Path to COCO annotations JSON (instances_val2017.json)")
    parser.add_argument("--pred", required=True, help="Path to predictions JSON (COCO results format)")
    parser.add_argument("--image_id", type=int, default=None, help="Optional: Evaluate only this specific image ID")
    parser.add_argument("--category_ids", type=str, default=None, help="Optional: Comma-separated list of category IDs to evaluate (e.g., '1,3,5')")
    parser.add_argument("--times_csv", default=None, help="Optional CSV with columns image_id,total_ms")
    parser.add_argument("--deadline_ms", type=float, default=None, help="Optional deadline in milliseconds")
    parser.add_argument("--out", default="coco_eval_report.json", help="Where to write summary JSON")
    args = parser.parse_args()

    # Lazy import pycocotools so this file can be opened without it installed
    try:
        from pycocotools.coco import COCO
        from pycocotools.cocoeval import COCOeval
    except Exception as e:
        print("ERROR: pycocotools is required. Install with `pip install pycocotools` (Linux/Mac) or `pip install pycocotools-windows` (Windows).")
        raise

    ann_path = Path(args.gt)
    pred_path = Path(args.pred)
    if not ann_path.exists():
        raise FileNotFoundError(f"Ground-truth annotations not found: {ann_path}")
    if not pred_path.exists():
        raise FileNotFoundError(f"Predictions file not found: {pred_path}")

    # Load COCO ground truth and detections
    coco_gt = COCO(str(ann_path))
    coco_dt = coco_gt.loadRes(str(pred_path))

    # Standard COCO eval (all predictions)
    coco_eval = COCOeval(coco_gt, coco_dt, iouType='bbox')

    if args.image_id is not None:
        print(f"INFO: Evaluating on single image_id: {args.image_id}")
        coco_eval.params.imgIds = [args.image_id]

    if args.category_ids is not None:
        try:
            cat_ids = [int(cid.strip()) for cid in args.category_ids.split(',')]
            print(f"INFO: Evaluating on specific category_ids: {cat_ids}")
            coco_eval.params.catIds = cat_ids
        except ValueError:
            print("ERROR: Invalid format for --category_ids. Please use a comma-separated list of integers (e.g., '1,3,5')")
            return

    coco_eval.evaluate()
    coco_eval.accumulate()
    coco_eval.summarize()

    summary_keys = [
        "AP@[.50:.95]",
        "AP@.50",
        "AP@.75",
        "AP_small",
        "AP_medium",
        "AP_large",
        "AR@[1]",
        "AR@[10]",
        "AR@[100]",
        "AR_small",
        "AR_medium",
        "AR_large",
    ]
    summary_vals = list(coco_eval.stats)
    summary = dict(zip(summary_keys, summary_vals))

    report = {
        "standard": summary,
        "deadline_ms": None,
        "map_at_deadline": None,
        "images_within_deadline": None,
        "images_total": None,
        "deadline_miss_rate": None,
    }

    # Optional deadline-aware evaluation
    if args.times_csv and args.deadline_ms is not None:
        import csv
        times = {}
        with open(args.times_csv, "r", newline="") as f:
            r = csv.DictReader(f)
            for row in r:
                try:
                    iid = int(row["image_id"])
                    t = float(row["total_ms"])
                    times[iid] = t
                except Exception:
                    continue

        # Determine which images meet the deadline
        within = {iid for iid, t in times.items() if t <= args.deadline_ms}

        # Filter predictions to those images
        with open(pred_path, "r") as f:
            preds = json.load(f)
        preds_deadline = [p for p in preds if int(p.get("image_id", -1)) in within]

        # Save a temporary JSON
        tmp_json = pred_path.parent / "_predictions_within_deadline.json"
        with open(tmp_json, "w") as f:
            json.dump(preds_deadline, f)

        coco_dt_d = coco_gt.loadRes(str(tmp_json))
        coco_eval_d = COCOeval(coco_gt, coco_dt_d, iouType='bbox')
        coco_eval_d.evaluate()
        coco_eval_d.accumulate()
        coco_eval_d.summarize()

        summary_d = dict(zip(summary_keys, list(coco_eval_d.stats)))
        report["deadline_ms"] = args.deadline_ms
        report["map_at_deadline"] = summary_d
        report["images_within_deadline"] = len(within)
        report["images_total"] = len(times)
        if len(times) > 0:
            report["deadline_miss_rate"] = 1.0 - (len(within) / len(times))
        else:
            report["deadline_miss_rate"] = None

    # Write report JSON
    with open(args.out, "w") as f:
        json.dump(report, f, indent=2)

    print("\n==== Summary (standard) ====")
    for k, v in report["standard"].items():
        print(f"{k:>16}: {v:.4f}")

    if report["map_at_deadline"]:
        print("\n==== Summary (within deadline) ====")
        print(f"Deadline (ms): {report['deadline_ms']}")
        print(f"Images within deadline: {report['images_within_deadline']} / {report['images_total']} "
              f"(miss rate={report['deadline_miss_rate']:.3f})")
        for k, v in report["map_at_deadline"].items():
            print(f"{k:>16}: {v:.4f}")

    print(f"\nSaved JSON report to: {args.out}")

if __name__ == "__main__":
    main()
