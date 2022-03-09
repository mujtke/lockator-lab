/**
 * 测试在去掉BAM之后对与函数调用的情况
 * 观察arg与线程创建时候的不同
 */


int b = 0;

void *thread1() {
	int a = 0;
	while(a < 3) {
		if (a > 1) {
			b = 2;
		}
		a++;
	}
}

void main() {
	b = 1;

	thread1();

	b = 3;

	return;
}