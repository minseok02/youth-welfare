import {
  DndContext,
  closestCenter,
  MouseSensor,
  TouchSensor,
  KeyboardSensor,
  useSensor,
  useSensors,
} from "@dnd-kit/core";
import {
  SortableContext,
  verticalListSortingStrategy,
  useSortable,
  sortableKeyboardCoordinates,
  arrayMove,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import DragIndicatorIcon from "@mui/icons-material/DragIndicator";
import CategoryIcon from "./CategoryIcon";

const A = "#2563eb";
const AS = "#e8efff";
const BG = "#f7f8fc";
const WHITE = "#fff";
const INK = "#11131a";
const INK3 = "#6b7280";
const LINE = "#e5e7eb";

function SortableRow({ id, index, meta, onRemove, compact }) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id });
  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    display: "flex",
    alignItems: "center",
    gap: compact ? 12 : 14,
    padding: compact ? "12px 14px" : "14px 16px",
    background: isDragging ? AS : BG,
    border: `1px solid ${isDragging ? A : LINE}`,
    borderRadius: compact ? 10 : 12,
    boxShadow: isDragging ? "0 8px 22px rgba(15,23,42,0.18)" : "none",
    position: "relative",
    zIndex: isDragging ? 1 : "auto",
    cursor: isDragging ? "grabbing" : "grab",
    userSelect: "none",
    WebkitUserSelect: "none",
    WebkitTouchCallout: "none",
  };
  const badgeSize = compact ? 24 : 28;
  return (
    <div ref={setNodeRef} style={style} {...attributes} {...listeners}>
      <DragIndicatorIcon sx={{ fontSize: compact ? 18 : 20, color: INK3, flexShrink: 0 }} />
      <span style={{ width: badgeSize, height: badgeSize, borderRadius: "50%", background: A, color: WHITE, fontSize: compact ? 11 : 13, fontWeight: 800, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>{index + 1}</span>
      <span style={{ width: badgeSize, height: badgeSize, borderRadius: 7, background: (meta?.fg || "#94a3b8") + "20", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
        <CategoryIcon name={meta?.icon} size={compact ? 15 : 17} color={meta?.fg} />
      </span>
      <span style={{ fontSize: compact ? 13 : 14, fontWeight: 700, flex: 1, color: INK }}>{meta?.label}</span>
      <button
        type="button"
        onPointerDown={(e) => e.stopPropagation()}
        onClick={() => onRemove(id)}
        aria-label="제거"
        style={{ background: "transparent", border: 0, color: INK3, fontSize: compact ? 14 : 16, cursor: "pointer", padding: compact ? "2px 6px" : "4px 8px", flexShrink: 0 }}
      >
        ✕
      </button>
    </div>
  );
}

export default function PrioritySortableList({ priorities, onReorder, onRemove, getMeta, compact = false }) {
  const sensors = useSensors(
    useSensor(MouseSensor, { activationConstraint: { distance: 5 } }),
    useSensor(TouchSensor, { activationConstraint: { delay: 180, tolerance: 6 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  const handleDragEnd = (event) => {
    const { active, over } = event;
    if (!over || active.id === over.id) return;
    const oldIndex = priorities.indexOf(active.id);
    const newIndex = priorities.indexOf(over.id);
    if (oldIndex === -1 || newIndex === -1) return;
    onReorder(arrayMove(priorities, oldIndex, newIndex));
  };

  return (
    <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
      <SortableContext items={priorities} strategy={verticalListSortingStrategy}>
        <div style={{ display: "flex", flexDirection: "column", gap: compact ? 6 : 8 }}>
          {priorities.map((val, i) => (
            <SortableRow key={val} id={val} index={i} meta={getMeta(val)} onRemove={onRemove} compact={compact} />
          ))}
        </div>
      </SortableContext>
    </DndContext>
  );
}
