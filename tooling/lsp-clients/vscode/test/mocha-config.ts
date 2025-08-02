// Centralized Mocha configuration
export interface MochaConfig {
    ui: 'bdd' | 'tdd' | 'qunit' | 'exports';
    timeout: number;
    color?: boolean;
    reporter?: string;
}

export const commonConfig: MochaConfig = {
    ui: 'bdd',
    timeout: 10000,
};

export const nodeConfig: MochaConfig = {
    ...commonConfig,
    color: true,
};

export const browserConfig: MochaConfig = {
    ...commonConfig,
    reporter: undefined
};