"""Split the supplied flattened OBJ conversion into its original visual stages."""
import copy
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "resource-pack/assets/skysignals/models/item/meteor.json"
TARGET = SOURCE.parent
source = json.loads(SOURCE.read_text(encoding="utf-8"))
elements = source["elements"]
assert len(elements) == 46

# OBJ order follows the animation construction: meteor, rings, debris, fire, border.
stages = {
    "meteor_impact_1": [0] + list(range(1, 9)),
    "meteor_impact_2": [0] + list(range(1, 17)) + list(range(42, 46)),
    "meteor_impact_3": [0] + list(range(1, 26)) + list(range(42, 46)),
    "meteor_impact_4": list(range(46)),
}
for name, indexes in stages.items():
    model = {"credit": "Supplied meteor animation stage", "textures": source["textures"],
             "elements": [elements[index] for index in indexes]}
    (TARGET / f"{name}.json").write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")

# The first OBJ element is the flying meteor. Recenter and enlarge it independently.
flight = copy.deepcopy(elements[0])
origin = flight["rotation"]["origin"]
half = [(flight["to"][axis] - flight["from"][axis]) / 2 for axis in range(3)]
factor = 14.0 / max(value * 2 for value in half)
flight["from"] = [8 - value * factor for value in half]
flight["to"] = [8 + value * factor for value in half]
flight["rotation"]["origin"] = [8, 8, 8]
model = {"credit": "Flying element from supplied meteor animation",
         "textures": source["textures"], "elements": [flight]}
(TARGET / "meteor_flight_source.json").write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")
print("Generated source flight model and 4 impact animation stages")
