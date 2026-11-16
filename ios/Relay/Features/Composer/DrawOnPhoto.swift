import RelayRemote
import SwiftUI

/// The sketch editor over one of the draft's photos. It opens on the photo as it was picked with
/// the strokes so far, and hands back the photo with the new ink burned in, or the untouched
/// photo once every stroke is undone.
struct DrawOnPhoto: View {
    let photo: PickedPhoto
    let drawn: (PickedPhoto) -> Void
    let failed: (String) -> Void
    let close: () -> Void

    private var original: PastedImage { photo.sketch?.original ?? photo.image }

    var body: some View {
        if let source = decoded(original) {
            SketchEditor(source: source, strokes: photo.sketch?.strokes ?? [], cancel: close) { strokes, burned in
                await save(strokes, burned)
                close()
            }
        } else {
            Color.black.onAppear {
                failed("Couldn't open that photo.")
                close()
            }
        }
    }

    private func save(_ strokes: [Stroke], _ burned: UIImage?) async {
        let original = original
        guard let burned else {
            if let untouched = PickedPhoto(restoring: original) { drawn(untouched) }
            return
        }
        let name = photo.image.name
        guard var made = await Task.detached(operation: { pickedPhoto(burned, name: name) }).value else {
            return failed("That photo is too big to send.")
        }
        made.sketch = Sketched(original: original, strokes: strokes)
        drawn(made)
    }
}
