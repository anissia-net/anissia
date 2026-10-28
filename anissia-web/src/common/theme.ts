import {shallowRef} from 'vue';

export type ColorMode = 'light' | 'dark';

class Theme {
    constructor() {
        this.apply();
    }

    private readonly mode = shallowRef<ColorMode>('light');

    public get value(): ColorMode {
        return this.mode.value;
    }

    private read(): ColorMode {
        return (
            (localStorage.getItem("theme") || (matchMedia("(prefers-color-scheme: dark)").matches ? 'dark' : 'light')) == 'dark'
                ? 'dark' : 'light'
        )
    }

    public set value(theme: string) {
        localStorage.setItem("theme", theme == 'dark' ? 'dark' : 'light');
        this.apply();
    }

    public toggle() {
        this.value = this.value == 'dark' ? 'light' : 'dark';
    }

    public apply() {
        const value = this.read();
        const classes = document.documentElement.classList;
        classes.toggle('dark', value === 'dark');
        classes.toggle('light', value === 'light');
        this.mode.value = value;
    }
}

export default new Theme();
