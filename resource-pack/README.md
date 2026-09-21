# SkySignals resource pack

This pack targets Minecraft Java 1.21.11 (resource pack format 75).

The meteor uses the item model `skysignals:meteor`. SkySignals applies that
model to a `PAPER` item shown by an `ItemDisplay`; the material, scale, rotation,
descent duration and approach trajectory can be changed in
`events-config.meteor.model`.

Build the client pack with:

```text
gradlew resourcePack
```

Host the resulting ZIP without extracting or modifying it, then configure its
URL and SHA-1 in `server.properties`. Players who decline or cannot download the
pack see the configured base item instead of the custom meteor.

The generated Java model comes from the supplied Blockbench OBJ export. Run
`python tools/convert_meteor.py meteor.obj <output.json>` to regenerate it.
