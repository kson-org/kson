#!/usr/bin/env node
import {createConnection} from 'vscode-languageserver/node';
import {startKsonServer} from './index.node.js';

// Under `--stdio`, console output goes to the client as log messages, so stdout carries only the protocol.
startKsonServer(createConnection());
