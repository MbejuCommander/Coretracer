package dev.coretrace.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FactoryResetTest {
 @Test void defaultsPreserveIndependentCopiesOfUserData() {
  Config c=new Config(); c.smartDelay=true; c.delayMs=5000; c.language="en_us"; c.queueExcel=true;
  TaskDefinition t=new TaskDefinition(); t.command="co l a:chat"; t.csvName="keep";
  c.tasks.add(t); c.saveTaskProfile("saved"); c.favorites.add(t.command); c.recentQueries.add(t.command); c.lastExportFile="keep.csv";
  Config reset=c.factoryDefaults();
  assertFalse(reset.smartDelay); assertEquals(1500,reset.delayMs); assertEquals("es_es",reset.language); assertFalse(reset.queueExcel);
  assertEquals("saved",reset.taskProfiles.getFirst().name); assertEquals("keep",reset.tasks.getFirst().csvName);
  assertEquals(c.favorites,reset.favorites); assertEquals(c.recentQueries,reset.recentQueries); assertEquals("keep.csv",reset.lastExportFile);
  reset.tasks.getFirst().csvName="changed"; assertEquals("keep",c.tasks.getFirst().csvName);
  assertEquals("keep",reset.taskProfiles.getFirst().tasks.getFirst().csvName);
 }
}
