import {Connection} from "vscode-languageserver";
import {FormattingService} from "../features/FormattingService";
import {KsonDocumentsManager} from "../document/KsonDocumentsManager";
import {CommandExecutorBase} from "./CommandExecutor.base";

export type CommandExecutorFactory = (
    connection: Connection,
    documentManager: KsonDocumentsManager,
    formattingService: FormattingService,
    workspaceRoot: string | null
) => CommandExecutorBase;
