class Theme {
    constructor() {
        this.apply();
    }

    private lastValue = '';

    public get value(): string {
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
        const value = this.value;
        if (this.lastValue != value) {
            const classes = document.querySelector('html')!!.classList;
            classes.remove("dark", 'light');
            classes.add(value);
            this.lastValue = value;
        }
    }
}

export default new Theme();