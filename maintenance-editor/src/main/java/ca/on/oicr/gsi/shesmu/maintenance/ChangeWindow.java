package ca.on.oicr.gsi.shesmu.maintenance;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public enum ChangeWindow {
  START_EARLIER {
    @Override
    public LocalDateTime changeStart(LocalDateTime initial, long duration, ChronoUnit unit) {
      return initial.minus(duration, unit);
    }

    @Override
    public LocalDateTime changeEnd(LocalDateTime initial, long duration, ChronoUnit unit) {
      return initial;
    }

    @Override
    public String toString() {
      return "Move start time earlier";
    }
  },
  START_LATER {
    @Override
    public LocalDateTime changeStart(LocalDateTime initial, long duration, ChronoUnit unit) {
      return initial.plus(duration, unit);
    }

    @Override
    public LocalDateTime changeEnd(LocalDateTime initial, long duration, ChronoUnit unit) {
      return initial;
    }

    @Override
    public String toString() {
      return "Move start time later";
    }
  },
  END_EARLIER {
    @Override
    public LocalDateTime changeStart(LocalDateTime initial, long duration, ChronoUnit unit) {
      return initial;
    }

    @Override
    public LocalDateTime changeEnd(LocalDateTime initial, long duration, ChronoUnit unit) {
      return initial.minus(duration, unit);
    }

    @Override
    public String toString() {
      return "Move end time earlier";
    }
  },
  END_LATER {
    @Override
    public LocalDateTime changeStart(LocalDateTime initial, long duration, ChronoUnit unit) {
      return initial;
    }

    @Override
    public LocalDateTime changeEnd(LocalDateTime initial, long duration, ChronoUnit unit) {
      return initial.plus(duration, unit);
    }

    @Override
    public String toString() {
      return "Move end time later";
    }
  },
  MOVE_EARLIER {
    @Override
    public LocalDateTime changeStart(LocalDateTime initial, long duration, ChronoUnit unit) {
      return initial.minus(duration, unit);
    }

    @Override
    public LocalDateTime changeEnd(LocalDateTime initial, long duration, ChronoUnit unit) {
      return initial.minus(duration, unit);
    }

    @Override
    public String toString() {
      return "Move whole window earlier";
    }
  },
  MOVE_LATER {
    @Override
    public LocalDateTime changeStart(LocalDateTime initial, long duration, ChronoUnit unit) {
      return initial.plus(duration, unit);
    }

    @Override
    public LocalDateTime changeEnd(LocalDateTime initial, long duration, ChronoUnit unit) {
      return initial.plus(duration, unit);
    }

    @Override
    public String toString() {
      return "Move whole window later";
    }
  },
  EXPAND {
    @Override
    public LocalDateTime changeStart(LocalDateTime initial, long duration, ChronoUnit unit) {
      return initial.minus(duration, unit);
    }

    @Override
    public LocalDateTime changeEnd(LocalDateTime initial, long duration, ChronoUnit unit) {
      return initial.plus(duration, unit);
    }

    @Override
    public String toString() {
      return "Expand window";
    }
  },
  SHRINK {
    @Override
    public LocalDateTime changeStart(LocalDateTime initial, long duration, ChronoUnit unit) {
      return initial.plus(duration, unit);
    }

    @Override
    public LocalDateTime changeEnd(LocalDateTime initial, long duration, ChronoUnit unit) {
      return initial.minus(duration, unit);
    }

    @Override
    public String toString() {
      return "Shrink window";
    }
  };

  public abstract LocalDateTime changeEnd(LocalDateTime initial, long duration, ChronoUnit unit);

  public abstract LocalDateTime changeStart(LocalDateTime initial, long duration, ChronoUnit unit);
}
