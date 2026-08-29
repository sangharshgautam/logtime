/* ==========================================================
File:        Heartbeat.java
Description: Stores coding activity waiting to be sent to the api.
Maintainer:  LogTime <support@logtime.com>
License:     BSD, see LICENSE for more details.
Website:     https://logtime.com/
===========================================================*/

package uk.co.sangharsh.logtime.plugin;

import java.math.BigDecimal;

public class Heartbeat {
    public String id;
    public String entity;
    public String localFile;
    public Integer lineCount;
    public Integer lineNumber;
    public Integer cursorPosition;
    public Integer humanLineChanges;
    public BigDecimal timestamp;
    public Boolean isWrite;
    public Boolean isUnsavedFile;
    public String project;
    public String language;
    public Boolean isBuilding;
    public Long timePassed;
    public Integer failCount;
    public String owner;
    public Long claimedAt;
}
