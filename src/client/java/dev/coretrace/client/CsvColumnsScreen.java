package dev.coretrace.client;

import dev.coretrace.core.Report;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

final class CsvColumnsScreen extends BaseScreen {
    private int page;
    CsvColumnsScreen(Screen parent) { super(parent, "ui.title.csv_columns"); }
    @Override protected void init() {
        super.init(); int half=(panelWidth-6)/2;
        int perPage = Math.max(1, (height - 121) / 25) * 2;
        int pageCount = (Report.CSV_COLUMNS.size() + perPage - 1) / perPage;
        page = Math.min(page, pageCount - 1); int start = page * perPage;
        for (int i=start;i<Math.min(start+perPage,Report.CSV_COLUMNS.size());i++) {
            final String key=Report.CSV_COLUMNS.get(i); int row=(i-start)/2, col=(i-start)%2;
            int x=left+col*(half+6), y=58+row*25;
            button((mod.config().csvColumns.contains(key)?"§a✓ ":"§7□ ")+key,x,y,half,()->{
                var c=mod.config(); if(c.csvColumns.contains(key)) { if(c.csvColumns.size()>1)c.csvColumns.remove(key); }
                else c.csvColumns.add(key); mod.saveConfig(); rebuildWidgets();
            });
        }
        button(tr("common.previous"),left,height-54,half,()->{page--;rebuildWidgets();}).active = page > 0;
        button(tr("common.next"),left+half+6,height-54,panelWidth-half-6,()->{page++;rebuildWidgets();}).active = page + 1 < pageCount;
        button(tr("common.back"),left, height-28,panelWidth,this::onClose);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        super.extractRenderState(g,mx,my,delta); label(g,tr("ui.settings.csv_columns_hint"),left,40,panelWidth,0xFFB8C9D9);
    }
}
