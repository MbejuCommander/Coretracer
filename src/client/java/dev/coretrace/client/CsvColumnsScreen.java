package dev.coretrace.client;

import dev.coretrace.core.CsvColumnSelection;
import dev.coretrace.core.Report;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

final class CsvColumnsScreen extends BaseScreen {
    private int page;
    private final dev.coretrace.core.Config target;
    private final Runnable persist;
    private final CsvColumnSelection selected;
    CsvColumnsScreen(Screen parent) { this(parent, CoreTraceClient.INSTANCE.config(), CoreTraceClient.INSTANCE::saveConfig); }
    CsvColumnsScreen(Screen parent, dev.coretrace.core.Config target, Runnable persist) {
        super(parent, "ui.title.csv_columns"); this.target = target; this.persist = persist;
        this.selected = new CsvColumnSelection(target.csvColumns);
    }
    private void selectionChanged() {
        if (selected.canSave()) {
            target.csvColumns = new java.util.ArrayList<>(selected.selected());
            persist.run();
        }
        rebuildWidgets();
    }
    @Override protected void init() {
        super.init(); int half=(panelWidth-6)/2;
        boolean allSelected = selected.allSelected();
        button(tr(allSelected ? "ui.settings.csv_clear_all" : "ui.settings.csv_select_all"),
                left + (panelWidth - 140) / 2, 37, 140, () -> {
                    selected.toggleAll();
                    selectionChanged();
                });
        int perPage = Math.max(1, (height - 121) / 25) * 2;
        int pageCount = (Report.CSV_COLUMNS.size() + perPage - 1) / perPage;
        page = Math.min(page, pageCount - 1); int start = page * perPage;
        for (int i=start;i<Math.min(start+perPage,Report.CSV_COLUMNS.size());i++) {
            final String key=Report.CSV_COLUMNS.get(i); int row=(i-start)/2, col=(i-start)%2;
            int x=left+col*(half+6), y=58+row*25;
            boolean warning = key.equals("server_timestamp") && !selected.contains(key);
            button((selected.contains(key)?"§a✓ ":"§7□ ")+key,x,y,warning ? half - 24 : half,()->{
                selected.toggle(key);
                selectionChanged();
            });
            if (warning) addRenderableWidget(new DelayWarningButton(x + half - 20, y, tr("smart.timestamp_warning"), 0xFFFF4545));
        }
        button(tr("common.previous"),left,height-54,half,()->{page--;rebuildWidgets();}).active = page > 0;
        button(tr("common.next"),left+half+6,height-54,panelWidth-half-6,()->{page++;rebuildWidgets();}).active = page + 1 < pageCount;
        button(tr("common.back"),left, height-28,panelWidth,this::onClose).active = selected.canSave();
    }
    @Override public void onClose() { if (selected.canSave()) super.onClose(); }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        super.extractRenderState(g,mx,my,delta); label(g,tr("ui.settings.csv_columns_hint"),left,40,panelWidth,0xFFB8C9D9);
    }
}
