package ca.on.oicr.gsi.shesmu.maintenance;

import com.github.lgooddatepicker.tableeditors.DateTimeTableEditor;
import java.awt.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;

public class Editor extends JFrame {
  private static class DateTableModel extends AbstractTableModel {
    private static final long serialVersionUID = 1L;
    final List<LocalDateTime[]> rows = new ArrayList<>();

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      return columnIndex == 2 ? String.class : LocalDateTime.class;
    }

    @Override
    public int getColumnCount() {
      return 3;
    }

    @Override
    public String getColumnName(int column) {
      switch (column) {
        case 0:
          return "Start";
        case 1:
          return "End";
        case 2:
          return "Duration";
      }
      throw new IllegalArgumentException();
    }

    @Override
    public int getRowCount() {
      return rows.size();
    }

    @Override
    public Object getValueAt(int row, int col) {
      final var rowValues = rows.get(row);
      return col == 2 ? Duration.between(rowValues[0], rowValues[1]).toString() : rowValues[col];
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return columnIndex != 2;
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      rows.get(rowIndex)[columnIndex] = (LocalDateTime) aValue;
    }
  }

  private static final long serialVersionUID = 1L;
  private static final Pattern TAB = Pattern.compile("\t");

  public static void main(String[] args) throws IOException {
    File file;
    if (args.length == 0) {
      final var chooser = new JFileChooser();
      if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        file = chooser.getSelectedFile();
      } else {
        return;
      }
    } else if (args.length == 1) {
      file = new File(args[0]);
    } else {
      System.err.println("Usage: java -jar maintenance-editor.jar [maintenance.tsv]");
      System.exit(1);
      return;
    }
    new Editor(file).setVisible(true);
  }

  public Editor(File file) {
    setTitle(file.toString() + " - Maintenance Schedule Editor");
    setSize(700, 400);
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    final var panel = new JPanel(new BorderLayout());
    add(panel);
    final var toolbar = new JToolBar();
    panel.add(toolbar, BorderLayout.PAGE_START);
    final var add = new JButton("Add");
    toolbar.add(add);
    final var delete = new JButton("Delete");
    toolbar.add(delete);
    final var reload = new JButton("Revert");
    toolbar.add(reload);
    final var sort = new JButton("Sort");
    toolbar.add(sort);
    final var save = new JButton("Save");
    toolbar.add(save);
    final var change = new JButton("Change Window");
    toolbar.add(change);
    final var model = new DateTableModel();
    final var table =
        new JTable(model) {
          private static final long serialVersionUID = 1L;

          public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
            final var c = (JComponent) super.prepareRenderer(renderer, row, column);
            if (model.rows.get(row)[0].isAfter(model.rows.get(row)[1])) {
              c.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, Color.RED));
            }
            return c;
          }
        };
    final var editor = new DateTimeTableEditor();
    editor.clickCountToEdit = 0;
    table.setDefaultEditor(LocalDateTime.class, editor);
    table.setDefaultRenderer(LocalDateTime.class, new DateTimeTableEditor());
    table.setFillsViewportHeight(true);
    panel.add(new JScrollPane(table), BorderLayout.CENTER);
    final Runnable loadFile =
        () -> {
          try (var lines = Files.lines(file.toPath())) {
            model.rows.clear();
            lines //
                .map(TAB::split) //
                .filter(x -> x.length > 1) //
                .forEach(x -> model.rows.add(new LocalDateTime[] {parse(x[0]), parse(x[1])}));
          } catch (IOException e) {
            JOptionPane.showMessageDialog(
                this,
                e.getMessage(),
                "Save Failed",
                JOptionPane.ERROR_MESSAGE,
                UIManager.getIcon("OptionPane.errorIcon"));
          }
        };
    loadFile.run();

    add.addActionListener(
        event -> {
          model.rows.add(new LocalDateTime[] {LocalDateTime.now(), LocalDateTime.now()});
          model.fireTableStructureChanged();
        });
    delete.addActionListener(
        event -> {
          Arrays.stream(table.getSelectedRows()).forEach(model.rows::remove);
          model.fireTableStructureChanged();
        });
    save.addActionListener(
        event -> {
          if (table.isEditing()) table.getCellEditor().stopCellEditing();
          try (var writer = new PrintWriter(file)) {
            for (var row : model.rows) {
              writer.write(toUtc(row[0]));
              writer.write("\t");
              writer.write(toUtc(row[1]));
              writer.write("\n");
            }
          } catch (FileNotFoundException e) {
            JOptionPane.showMessageDialog(
                this,
                e.getMessage(),
                "Save Failed",
                JOptionPane.ERROR_MESSAGE,
                UIManager.getIcon("OptionPane.errorIcon"));
          }
        });
    sort.addActionListener(
        event -> {
          if (table.isEditing()) table.getCellEditor().stopCellEditing();
          model.rows.sort(
              Comparator.<LocalDateTime[], LocalDateTime>comparing(r -> r[0])
                  .thenComparing(r -> r[1]));
          model.fireTableStructureChanged();
        });
    reload.addActionListener(
        event -> {
          if (table.isEditing()) table.getCellEditor().stopCellEditing();
          loadFile.run();
          model.fireTableStructureChanged();
        });
    change.addActionListener(
        event -> {
          if (table.getSelectedRowCount() == 0) {
            JOptionPane.showMessageDialog(
                this, "Please select rows first.", "Maintenance Editor", JOptionPane.ERROR_MESSAGE);
          } else {
            final var spinner = new JSpinner(new SpinnerNumberModel(1, 0, 100, 1));
            ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setColumns(4);
            final var changeWindowBox = new JComboBox<>(ChangeWindow.values());
            final var units =
                new JComboBox<>(
                    Stream.of(ChronoUnit.values())
                        .filter(
                            u ->
                                u.ordinal() > ChronoUnit.SECONDS.ordinal()
                                    && u.ordinal() < ChronoUnit.MONTHS.ordinal())
                        .toArray(ChronoUnit[]::new));
            final var input = new JPanel(new GridBagLayout());
            final var labelConstraints = new GridBagConstraints();
            labelConstraints.gridwidth = 3;
            final var changeWindowConstraints = new GridBagConstraints();
            changeWindowConstraints.gridx = 0;
            changeWindowConstraints.gridy = 1;
            final var spinnerConstraints = new GridBagConstraints();
            spinnerConstraints.gridx = 1;
            spinnerConstraints.gridy = 1;
            spinnerConstraints.anchor = GridBagConstraints.LINE_END;
            spinnerConstraints.weightx = 1.0;
            spinnerConstraints.insets = new Insets(5, 5, 5, 5);
            final var unitsConstraints = new GridBagConstraints();
            unitsConstraints.gridx = 2;
            unitsConstraints.gridy = 1;
            input.add(new JLabel("Change the time windows by:"), labelConstraints);
            input.add(changeWindowBox, changeWindowConstraints);
            input.add(spinner, spinnerConstraints);
            input.add(units, unitsConstraints);
            if (JOptionPane.showOptionDialog(
                    this,
                    input,
                    "Expand Windows - Maintenance Editor",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    null,
                    null)
                == JOptionPane.OK_OPTION) {
              if (table.isEditing()) table.getCellEditor().stopCellEditing();
              final long value = (Integer) spinner.getValue();
              final var changeWindow = (ChangeWindow) changeWindowBox.getSelectedItem();
              final var unit = (ChronoUnit) units.getSelectedItem();
              for (var row : table.getSelectedRows()) {
                var times = model.rows.get(row);
                times[0] = changeWindow.changeStart(times[0], value, unit);
                times[1] = changeWindow.changeEnd(times[1], value, unit);
              }
              model.fireTableDataChanged();
            }
          }
        });
  }

  private LocalDateTime parse(String string) {
    return ZonedDateTime.parse(string)
        .withZoneSameInstant(ZoneId.systemDefault())
        .toLocalDateTime();
  }

  private String toUtc(LocalDateTime local) {
    return local
        .atZone(ZoneId.systemDefault())
        .withZoneSameInstant(ZoneId.of("Z"))
        .truncatedTo(ChronoUnit.SECONDS)
        .toString();
  }
}
