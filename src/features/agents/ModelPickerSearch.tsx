import type { RefObject } from "react";
import { Combobox } from "@base-ui/react/combobox";
import { Search } from "lucide-react";

/** The picker's search field; Escape closes the picker instead of clearing it. */
export function ModelPickerSearch({
  input,
  close,
}: {
  input: RefObject<HTMLInputElement | null>;
  close: () => void;
}) {
  return (
    <div className="model-picker-search">
      <Search size={16} aria-hidden />
      <Combobox.Input
        ref={input}
        aria-label="Search models"
        placeholder="Search models…"
        autoComplete="off"
        spellCheck={false}
        onKeyDown={(e) => {
          if (e.key === "Escape") {
            e.preventDefault();
            e.stopPropagation();
            close();
          }
        }}
      />
    </div>
  );
}
