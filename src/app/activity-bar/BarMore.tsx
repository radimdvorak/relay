import { Menu } from "@base-ui/react/menu";
import { Ellipsis } from "lucide-react";
import { MenuAction, MenuPopup } from "../../features/sidebar/SidebarMenu";
import { BarDot, type BarEntry } from "./ActivityBarButton";

/**
 * The icons that didn't fit along the sidebar's bottom. A dot on one of
 * them shows on the "…" too, so nothing waiting gets folded out of sight.
 */
export function BarMore({ items }: { items: BarEntry[] }) {
  const waiting = items.find((item) => item.dot);
  const label = "More";
  return (
    <Menu.Root>
      <Menu.Trigger
        className={`activity-bar-button activity-bar-more ${items.some((item) => item.current) ? "active" : ""}`}
        aria-label={waiting ? `${label} · ${waiting.dot}` : label}
        title={waiting ? `${label} · ${waiting.dot}` : label}
      >
        <Ellipsis size={16} />
        {waiting && <BarDot status={waiting.status} />}
      </Menu.Trigger>
      <MenuPopup side="top" align="end">
        {items.map((item) => (
          <MenuAction
            key={item.label}
            icon={item.icon}
            hint={item.dot ?? item.keys}
            aria-current={item.current ? "page" : undefined}
            onClick={item.onClick}
          >
            {item.label}
          </MenuAction>
        ))}
      </MenuPopup>
    </Menu.Root>
  );
}
