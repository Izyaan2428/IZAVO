package com.izavo.app.importing

import java.io.Reader

interface BankStatementParser { fun parse(reader: Reader): ImportParseResult }
