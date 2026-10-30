package com.falazar.farmupcraft.database;

import com.falazar.farmupcraft.database.serializers.StringDataSerializer;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import org.slf4j.Logger;

public class DataBaseTest {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final DataBase<String, String> dataBase;

    public DataBaseTest(DataBase<String, String> dataBase) {
        this.dataBase = dataBase;
    }

    public void runTests() {
        testPutAndGetData();
        testCleanUpExpiredData();
        testTransaction();
        testSaveAndLoad();
        testPutDataWithNullKey();
        testPutDataWithNullValue();

    }

    private void testPutAndGetData() {
        LOGGER.info("Starting testPutAndGetData...");
        try {
            dataBase.putData("key1", "value1");
            String value = dataBase.getData("key1");
            Assert.assertNotNull(value, "Value should not be null");
            Assert.assertEquals("value1", value, "The retrieved value should match the stored value");
            LOGGER.info("testPutAndGetData passed.");
        } catch (Exception e) {
            LOGGER.error("testPutAndGetData failed: " + e.getMessage());
        }
    }

    private void testCleanUpExpiredData() {
        LOGGER.info("Starting testCleanUpExpiredData...");
        try {
            dataBase.putData("key1", "value1");

            // Simulate passage of time
            Thread.sleep(2000); // Sleep for 2 seconds to simulate time passing
            dataBase.cleanUpExpiredData();
            //String object = dataBase.getData("key1");
            //if (object == null) {
            //    LOGGER.info("key1 is null");
            //} else {
            //    LOGGER.error("Key1 is not null something is wrong");
            //}
            Assert.assertNull(dataBase.getData("key1"), "Expired data should be cleaned up");
            LOGGER.info("testCleanUpExpiredData passed.");
        } catch (Exception e) {
            LOGGER.error("testCleanUpExpiredData failed: " + e.getMessage());
        }
    }

    private void testTransaction() {
        LOGGER.info("Starting testTransaction...");
        try {
            dataBase.beginTransaction();
            dataBase.putData("key2", "value2");
            dataBase.rollbackTransaction();

            //String object = dataBase.getData("key2");
            //if (object == null) {
            //    LOGGER.info("key2 is null");
            //} else {
            //    LOGGER.error("key2 is not null something is wrong");
            //}
            Assert.assertNull(dataBase.getData("key2"), "Data should be rolled back");
            LOGGER.info("testTransaction passed.");
        } catch (Exception e) {
            LOGGER.error("testTransaction failed: " + e.getMessage());
        }
    }

    private void testSaveAndLoad() {
        LOGGER.info("Starting testSaveAndLoad...");
        try {
            dataBase.putData("key3", "value3");

            CompoundTag tag = new CompoundTag();
            dataBase.save(tag);

            // Simulate loading from saved state
            DataBase<String, String> newDataBase = new DataBase<>(tag, "testDB", null, new StringDataSerializer(), new StringDataSerializer(), true, 1000, false);
            Assert.assertEquals("value3", newDataBase.getData("key3"), "Data should be correctly loaded from saved state");
            newDataBase.putData("blablabla", "blabla");
            newDataBase.putData("blablabla2", "blabla2");
            newDataBase.putData("blablabla3", "blabla3");
            newDataBase.putData("blablabla4", "blabla4");
            newDataBase.putData("blablabla5", "blabla5");
            newDataBase.shutdown();
            LOGGER.info("testSaveAndLoad passed.");
        } catch (Exception e) {
            LOGGER.error("testSaveAndLoad failed: " + e.getMessage());
        }
    }

    private void testPutDataWithNullKey() {
        LOGGER.info("Starting testPutDataWithNullKey...");
        try {
            Assert.assertThrows(() -> dataBase.putData(null, "value"), "Key cannot be null");
            LOGGER.info("testPutDataWithNullKey passed.");
        } catch (Exception e) {
            LOGGER.error("testPutDataWithNullKey failed: " + e.getMessage());
        }
    }

    private void testPutDataWithNullValue() {
        LOGGER.info("Starting testPutDataWithNullValue...");
        try {
            dataBase.putData("key", null);
            //String string = dataBase.getData("key");
            //if (string == null) {
            //    LOGGER.info("key is null");
            //} else {
            //    LOGGER.error("key is not null something is wrong");
            //}
            Assert.assertNull(dataBase.getData("key"), "Value should be null if not explicitly handled");
            LOGGER.info("testPutDataWithNullValue passed.");
        } catch (Exception e) {
            LOGGER.error("testPutDataWithNullValue failed: " + e.getMessage());
        }
    }
}
