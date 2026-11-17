import type { RefObject } from "react";
import { Combobox } from "@base-ui/react/combobox";
import { Search } from "lucide-react";
import type { Category } from "./model-picker-catalog";
import { ModelRefresh, refreshesModels } from "./ModelRefresh";

/**
 * The picker's search field, with a way to ask the open agent for its models
 * again; Escape closes the picker instead of clearing it.
 */
export function ModelPickerSearch({
  input,
  close,
  category,
  computer,
}: {
  input: RefObject<HTMLInputElement | null>;
  close: () => void;
  category: Category;
  computer?: { id: string; name: string };
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
      {refreshesModels(category) && (
        <ModelRefresh key={category} provider={category} computer={computer} />
      )}
    </div>
  );
}
