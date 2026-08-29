import { v4 as uuidv4 } from 'uuid';

export default class UuidV4 {
    private readonly value: string;

    private constructor(value: string) {
        this.value = value;
    }

    public static generate(): UuidV4 {
        return new UuidV4(uuidv4());
    }

    public toString(): string {
        return this.value;
    }
}