

python3 -m venv venv
source venv/bin/activate
pip install pycocotools


python coco_eval_map.py \
--gt instances_val2017.json \
--pred predictions.json \
--times_csv times.csv \
--deadline_ms 50 \
--out coco_eval_report.json


