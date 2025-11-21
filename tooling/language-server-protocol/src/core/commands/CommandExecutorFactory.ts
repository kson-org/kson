import {Connection} from "vscode-languageserver";
import {FormattingService} from "../features/FormattingService";
import {KsonDocumentsManager} from "../document/KsonDocumentsManager";
import {KsonSettings} from "../KsonSettings";
import {CommandExecutorBase} from "./CommandExecutor.base";

export type CommandExecutorFactory = (
    connection: Connection,
    documentManager: KsonDocumentsManager,
    formattingService: FormattingService,
    getConfiguration: () => Required<KsonSettings>,
    workspaceRoot: string | null
) => CommandExecutorBase;
